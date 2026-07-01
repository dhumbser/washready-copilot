(function () {
    const af = window.authFetch;
    const escapeHtml = window.escapeHtml;

    const $ = (q) => document.querySelector(q);

    // ===== Estado global =====
    let me = null, roleAdmin = false;
    let currentId = null, currentEstado = 'NUEVO', editMode = false;
    let savingTicket = false;
    let currentOperarioId = null;
    let bonoMotivo = '';

    // ===== Utiles =====
    function getParam(name) {
        const p = new URLSearchParams(location.search).get(name);
        if (p == null) return null;
        const n = Number(p);
        return Number.isFinite(n) ? n : p;
    }
    function fmt(n) { return (Number(n || 0)).toFixed(2).replace('.', ',') + ' €'; }
    function highlightMatches(text, term) {
        const safe = escapeHtml(text);
        const terms = String(term || '').trim().split(/\s+/).filter(Boolean);
        if (!terms.length) return safe;
        const pattern = terms.map(t => t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|');
        if (!pattern) return safe;
        const regex = new RegExp(`(${pattern})`, 'gi');
        return safe.replace(regex, '<strong>$1</strong>');
    }
    function toDateOnlyInput(dt) {
        if (!dt) return '';
        if (typeof dt === 'string') {
            const s = dt.trim();
            if (s.length >= 10) return s.substring(0, 10);
        }
        const d = new Date(dt);
        const pad = n => String(n).padStart(2, '0');
        return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
    }
    function buildRefPlaceholder() {
        const name = (me?.centroTrabajo || '').normalize('NFD').replace(/[^\p{L}]/gu, '').toUpperCase();
        const pref = (name + 'XXX').slice(0, 3);
        const cid = me?.centroId ?? '';
        const yy = String(new Date().getFullYear() % 100).padStart(2, '0');
        return `${pref}${cid}-${yy}-…..`;
    }
    async function prettyHttpError(res, fallback = 'Error') {
        let raw = '';
        try { raw = (await res.text())?.trim(); } catch { }
        try {
            const j = raw ? JSON.parse(raw) : null;
            if (j?.message) return j.message;
            if (j?.detail) return j.detail;
            if (j?.error && res.status !== 409) return j.error;
        } catch { }
        if (res.status === 409) return 'Ya existe un cliente con ese teléfono';
        return raw || fallback;
    }

    // ===== Usuario actual =====
    async function whoAmI() {
        const r = await af('/auth/me', { headers: { 'Accept': 'application/json' } });
        if (r.status === 401) { location = '/login.html'; return; }
        me = await r.json();
        $('#username').textContent = me.usuario || '—';
        $('#centro').textContent = me.centroTrabajo || '—';
        roleAdmin = me?.role === 'ROLE_ADMIN';
        const refEl = document.getElementById('referencia');
        if (refEl) refEl.placeholder = buildRefPlaceholder();
    }
    async function refreshNextReferencia() {
        if (currentId) return;
        const refEl = document.getElementById('referencia');
        if (!refEl) return;
        try {
            const iso = new Date().toISOString();
            const qs = new URLSearchParams({ fecha: iso });
            const r = await af('/api/tickets/next-ref?' + qs.toString(), { headers: { 'Accept': 'application/json' } });
            if (!r.ok) return;
            const data = await r.json();
            if (data && data.referencia) refEl.value = data.referencia;
        } catch { }
    }

    // ===== Operarios =====
    let usersCache = null;
    async function fetchUsersMin() {
        if (usersCache) return usersCache;
        const res = await af('/api/users/min', { headers: { 'Accept': 'application/json' } });
        usersCache = res.ok ? await res.json() : [];
        return usersCache;
    }
    async function setOperarioFromMe() {
        try {
            const users = await fetchUsersMin();
            const self = users.find(u => u.usuario === (me?.usuario || ''));
            currentOperarioId = self ? self.id : null;
        } catch {
            currentOperarioId = null;
        }
    }
    async function setOperarioById(id) {
        currentOperarioId = id || null;
    }

    // ===== Vehículo + Cliente select =====
    const vehiculoSearch = $('#vehiculoSearch');
    const vehiculoList = $('#vehiculoList');
    const vehiculoId = $('#vehiculoId');
    const clienteId = $('#clienteId');

    let vehItems = [], vehTimer = null;
    let prevClienteSelection = '';

    function resetClienteSelect() {
        prevClienteSelection = '';
        clienteId.innerHTML = '<option value="">— Selecciona —</option>';
        clienteId.value = '';
        clienteId.disabled = true;
    }
    function enableClienteSelect() {
        clienteId.disabled = false;
    }

    async function searchVehiculos(term) {
        term = (term || '').trim();
        if (term.length < 2) { vehiculoList.style.display = 'none'; return; }

        const qs = new URLSearchParams({ q: term });
        const r = await af('/api/vehiculos/search?' + qs.toString(), { headers: { 'Accept': 'application/json' } });

        if (!r.ok) {
            vehiculoList.innerHTML = '<div class="autocomplete-item">Error</div>';
            vehiculoList.style.display = 'block';
            return;
        }

        const data = await r.json();
        vehItems = Array.isArray(data) ? data : (data.content || []);

        if (!vehItems.length) {
            vehiculoList.innerHTML = '<div class="autocomplete-item">Sin resultados</div>';
            vehiculoList.style.display = 'block';
            return;
        }

        vehiculoList.innerHTML = vehItems.map((v, i) => {
            const matricula = highlightMatches(v.matricula, term);
            const marca = v.marca ? ` · ${escapeHtml(v.marca)}` : '';
            const modelo = v.modelo ? ` ${escapeHtml(v.modelo)}` : '';
            const cliente = v.cliente ? ` · ${highlightMatches(v.cliente, term)}` : '';
            const telefono = v.telefono ? ` · ${highlightMatches(v.telefono, term)}` : '';
            return `<div class="autocomplete-item" data-i="${i}">${matricula}${marca}${modelo}${cliente}${telefono}</div>`;
        }).join('');
        vehiculoList.style.display = 'block';
    }

    async function loadClientesDeVehiculo(id, selectedClienteId = null) {
        if (!id) { resetClienteSelect(); return; }

        enableClienteSelect();
        clienteId.innerHTML = '<option value="">Cargando…</option>';

        const r = await af(`/api/vehiculos/${id}/clientes`, { headers: { 'Accept': 'application/json' } });
        const list = r.ok ? await r.json() : [];

        const options = list.map(c => {
            const full = escapeHtml(`${c.nombre || ''}${c.apellido ? (' ' + c.apellido) : ''}`.trim());
            const label = c.noDeseado ? `*** ${full} ***` : full;
            const ndAttr = c.noDeseado ? ' data-nd="1"' : '';
            return `<option value="${c.id}"${ndAttr}>${label}</option>`;
        }).join('');

        const addNew = `<option value="__NEW_CLIENT__">+ Añadir nuevo cliente…</option>`;

        clienteId.innerHTML =
            '<option value="">— Selecciona —</option>' +
            options +
            addNew;

        if (selectedClienteId) {
            clienteId.value = String(selectedClienteId);
            loadClientHistory(selectedClienteId);
        } else {
            $('#clientHistoryContainer').style.display = 'none';
        }
    }

    async function loadClientHistory(cid) {
        const container = $('#clientHistoryContainer');
        if (currentId || !editMode) { container.style.display = 'none'; return; }
        if (!cid || cid === '__NEW_CLIENT__') { container.style.display = 'none'; return; }

        container.innerHTML = '<div class="muted">Cargando historial…</div>';
        container.style.display = 'block';

        try {
            const r = await af(`/api/tickets/history/cliente/${cid}`, { headers: { 'Accept': 'application/json' } });
            if (!r.ok) { container.style.display = 'none'; return; }
            const hist = await r.json();

            if (!hist || !hist.length) {
                container.innerHTML = '<div class="muted" style="font-size:0.9em; font-style:italic;">Sin servicios anteriores recientes.</div>';
                return;
            }

            let html = '<div class="history-title">Últimos servicios <span style="font-weight:400; font-size:0.85em;">(3 últimos)</span></div>';
            hist.forEach(h => {
                const dateStr = h.fecha ? new Date(h.fecha).toLocaleDateString() : '—';
                const ref = h.referencia || `#${h.id}`;
                const tot = fmt(h.total);
                const mat = h.matricula ? ` · ${h.matricula}` : '';

                let detHtml = '';
                if (h.detalles && h.detalles.length) {
                    detHtml = '<div class="history-details">';
                    h.detalles.forEach(d => {
                        detHtml += `<div class="history-detail-row"><span>- ${escapeHtml(d.descripcion)}</span><span>${fmt(d.precio)}</span></div>`;
                    });
                    detHtml += '</div>';
                }

                html += `<div class="history-item">
                    <div class="history-item-header">
                        <span>${dateStr} · <strong>${ref}</strong>${mat}</span>
                        <span>${tot}</span>
                    </div>${detHtml}
                </div>`;
            });
            container.innerHTML = html;
        } catch {
            container.style.display = 'none';
        }
    }

    function selectVehiculoByIndex(i) {
        const v = vehItems[i];
        if (!v) return;

        prevClienteSelection = '';
        let display = v.matricula;
        if (v.marca) display += ' · ' + v.marca;
        if (v.modelo) display += ' ' + v.modelo;
        vehiculoSearch.value = display;
        vehiculoId.value = v.id;
        vehiculoList.style.display = 'none';

        loadClientesDeVehiculo(v.id, v.clienteId || null);
    }

    // ===== Servicios / líneas =====
    const itemSearch = $('#itemSearch');
    const itemList = $('#itemList');
    const tbodyLines = $('#tbodyLines');

    let itemTimer = null, itemItems = [];

    async function searchItems(term) {
        term = (term || '').trim();
        if (!term) { itemList.style.display = 'none'; return; }

        const centroId = me?.centroId ?? me?.centroID ?? me?.centro_id;
        if (!centroId && !roleAdmin) { itemList.style.display = 'none'; setMsg('Tu usuario no tiene centro asignado', 'error'); return; }

        const params = new URLSearchParams({ q: term, editable: 'false' });
        if (centroId && !roleAdmin) params.set('centroId', String(centroId));
        const r = await af('/api/servicios?' + params.toString(), { headers: { 'Accept': 'application/json' } });

        if (!r.ok) {
            itemList.innerHTML = '<div class="autocomplete-item">Error</div>';
            itemList.style.display = 'block';
            return;
        }

        const list = await r.json();
        itemItems = Array.isArray(list) ? list : [];

        if (!itemItems.length) {
            itemList.innerHTML = '<div class="autocomplete-item">Sin resultados</div>';
            itemList.style.display = 'block';
            return;
        }

        itemList.innerHTML = itemItems.map((it, i) => {
            const display = fmt(it.importeCeroEnTicket ? 0 : it.importe);
            const centroNombre = it.centrosNombres && it.centrosNombres.length ? it.centrosNombres[0] : null;
            return `<div class="autocomplete-item" data-i="${i}">${highlightMatches(it.descripcion, term)} · ${display}${centroNombre ? (' · ' + escapeHtml(centroNombre)) : ''}</div>`;
        }).join('');
        itemList.style.display = 'block';
    }

    function recompute() {
        let sum = 0;
        tbodyLines.querySelectorAll('tr').forEach(tr => {
            const price = parseFloat(tr.dataset.price);
            const qty = parseFloat(tr.querySelector('input.qty')?.value || '1');
            const line = price * qty;
            const lt = tr.querySelector('.lineTotal');
            if (lt) lt.textContent = fmt(line);
            sum += line;
        });
        const factor = 1.21, base = sum / factor, iva = sum - base;
        $('#base').textContent = fmt(base);
        $('#iva').textContent = fmt(iva);
        $('#total').textContent = fmt(sum);
    }

    function addLine(det, readOnly) {
        const tr = document.createElement('tr');

        tr.dataset.servicioId = det.servicioId ?? det.id;
        tr.dataset.desc = (det.descripcion || '').trim();
        tr.dataset.override = det.override ? '1' : '0';

        const unitPrice = Number(det.precio ?? 0);
        tr.dataset.price = unitPrice;

        const displayPrice = (det.precioTicket != null ? det.precioTicket : unitPrice);

        if (det.detalleId) tr.dataset.detalleId = det.detalleId;

        const badge = det.importeCeroEnTicket
            ? `<span class="badge-zero" title="En el ticket impreso se verá 0,00">Ticket: 0,00</span>`
            : '';

        tr.innerHTML = `
<td>${escapeHtml(tr.dataset.desc)}${badge}</td>
<td class="right">${fmt(displayPrice)}</td>
<td class="right">
<input class="qty" type="number" min="1" step="1" value="${det.cantidad || 1}" style="width:90px;" ${readOnly ? 'disabled' : ''}/>
</td>
<td class="right lineTotal">${fmt((det.cantidad || 1) * unitPrice)}</td>
<td>${readOnly ? '' : `<button class="secondary del">Quitar</button>`}</td>
`;

        tbodyLines.appendChild(tr);
    }

    async function loadDetalles(ticketId) {
        const r = await af(`/api/tickets/${ticketId}/detalles`, { headers: { 'Accept': 'application/json' } });
        if (!r.ok) { tbodyLines.innerHTML = ''; recompute(); return; }

        const lines = await r.json();
        tbodyLines.innerHTML = '';
        lines.forEach(d => {
            addLine({
                detalleId: d.id,
                servicioId: d.servicioId,
                descripcion: d.descripcion || '',
                precio: d.precio,
                precioTicket: d.precioTicket,
                cantidad: d.cantidad,
                importeCeroEnTicket: d.importeCeroEnTicket
            }, true);
        });
        recompute();
    }

    // ===== Estados / UI =====
    const EST = { NUEVO: 'NUEVO', PTE_PAGO: 'PTE_PAGO', PAGADO: 'PAGADO', CERRADO: 'CERRADO', ANULADO: 'ANULADO' };

    function normalizeEstado(raw) {
        if (raw == null) return EST.NUEVO;
        let s = String(raw).trim().toUpperCase();
        s = s.normalize('NFD').replace(/[̀-ͯ]/g, '');
        const compact = s.replace(/[\s._-]/g, '');

        if (/^\d+$/.test(compact)) {
            switch (Number(compact)) {
                case 1: return EST.PTE_PAGO;
                case 2: return EST.PAGADO;
                case 3: return EST.CERRADO;
                case 4: return EST.ANULADO;
                default: return EST.NUEVO;
            }
        }
        if (compact === 'PTEDEPAGO' || compact === 'PENDIENTEPAGO' || compact === 'PTEPAGO') return EST.PTE_PAGO;
        if (compact === 'PAGADO' || compact === 'PAGADA' || compact === 'PAID') return EST.PAGADO;
        if (compact === 'CERRADO' || compact === 'CERRADA' || compact === 'CLOSED') return EST.CERRADO;
        if (compact === 'ANULADO' || compact === 'ANULADA' || compact === 'CANCELADO' || compact === 'CANCELADA' || compact === 'VOID') return EST.ANULADO;

        return EST.NUEVO;
    }

    function updateActionButtons() {
        const bGuardar = document.getElementById('btnGuardar');
        const bNuevoVeh = document.getElementById('btnNuevoVeh');
        const bImprimir = document.getElementById('btnImprimir');
        const bNuevo = document.getElementById('btnNuevo');

        const showPostPago = !!currentId && [EST.PTE_PAGO, EST.PAGADO, EST.CERRADO, EST.ANULADO].includes(currentEstado);

        if (!currentId) {
            if (bGuardar) {
                bGuardar.style.display = '';
                bGuardar.disabled = savingTicket;
                bGuardar.textContent = savingTicket ? 'Creando...' : 'Crear';
            }
            if (bNuevoVeh) bNuevoVeh.style.display = '';
            if (bImprimir) bImprimir.style.display = 'none';
            if (bNuevo) bNuevo.style.display = 'none';
        } else {
            if (bGuardar) {
                bGuardar.style.display = 'none';
                bGuardar.disabled = false;
                bGuardar.textContent = 'Guardar';
            }
            if (bNuevoVeh) bNuevoVeh.style.display = 'none';
            if (bImprimir) bImprimir.style.display = showPostPago ? '' : 'none';
            if (bNuevo) bNuevo.style.display = showPostPago ? '' : 'none';
        }
    }

    function setEditable(on) {
        if (currentId) on = false;
        editMode = !!on;
        const lock = !!currentId || !editMode;

        ['comentarios', 'vehiculoSearch', 'itemSearch']
            .forEach(id => { const el = document.getElementById(id); if (el) el.disabled = lock; });
        if (lock) clienteId.disabled = true;

        tbodyLines.querySelectorAll('input.qty').forEach(inp => inp.disabled = lock);
        tbodyLines.querySelectorAll('button.del').forEach(btn => btn.style.display = lock ? 'none' : '');

        const addServRow = document.getElementById('addServicesRow');
        if (addServRow) addServRow.style.display = lock ? 'none' : '';
        const addServTitle = document.getElementById('addServicesTitle');
        if (addServTitle) addServTitle.style.display = lock ? 'none' : '';

        if (lock) $('#clientHistoryContainer').style.display = 'none';

        updatePayControls();
        updateActionButtons();
    }

    function updatePayControls() {
        const box = document.getElementById('payBox');
        if (!box) return;

        const sel = document.getElementById('metodoPago');
        const mp = normalizeMetodoPago(sel?.value);

        const showForBono = !!currentId && mp === MP.BONO && currentEstado !== EST.NUEVO;
        const visible = !!currentId && (currentEstado === EST.PTE_PAGO || showForBono);

        box.style.display = visible ? '' : 'none';
        if (sel) sel.disabled = currentEstado !== EST.PTE_PAGO;

        const btnPay = document.getElementById('btnConvertirPagado');
        if (btnPay) btnPay.style.display = currentEstado === EST.PTE_PAGO ? '' : 'none';

        updateBonoControls();
    }

    function updateDeliverControls() {
        const box = document.getElementById('deliverBox');
        if (!box) return;
        const visible = !!currentId && currentEstado === EST.PAGADO;
        box.style.display = visible ? '' : 'none';
    }

    function setStatus(s) {
        const st = normalizeEstado(s);
        ['stNuevo', 'stPte', 'stPagado', 'stCerrado', 'stAnulado'].forEach(id => document.getElementById(id).classList.remove('on'));

        document.getElementById('stNuevo').classList.toggle('on', st === EST.NUEVO);
        document.getElementById('stPte').classList.toggle('on', st === EST.PTE_PAGO);
        document.getElementById('stPagado').classList.toggle('on', st === EST.PAGADO);
        document.getElementById('stCerrado').classList.toggle('on', st === EST.CERRADO);
        document.getElementById('stAnulado').classList.toggle('on', st === EST.ANULADO);

        currentEstado = st;
        updatePayControls();
        updateDeliverControls();
        updateActionButtons();
        updateCancelControls();
        updateNdButton();
    }

    // ===== Método de pago / bono =====
    const MP = { EFECTIVO: 'EFECTIVO', TARJETA: 'TARJETA', BIZUM: 'BIZUM', BONO: 'BONO', TRANSFERENCIA: 'TRANSFERENCIA', OTRO: 'OTRO' };
    function normalizeMetodoPago(raw) {
        if (!raw) return '';
        return String(raw).trim().toUpperCase().replace(/\s+/g, '_');
    }

    function updateBonoControls() {
        const holder = document.getElementById('bonoControls');
        const mpSel = document.getElementById('metodoPago');
        if (!holder || !mpSel) return;

        const mp = normalizeMetodoPago(mpSel.value);
        const estadoPermiteBono = [EST.PTE_PAGO, EST.PAGADO, EST.CERRADO, EST.ANULADO].includes(currentEstado);
        const visible = !!currentId && estadoPermiteBono && mp === MP.BONO;

        holder.style.display = visible ? '' : 'none';
        const prev = document.getElementById('bonoMotivoPreview');
        if (prev) {
            const hasMot = bonoMotivo && bonoMotivo.trim();
            prev.textContent = hasMot ? bonoMotivo.trim() : 'Sin registrar';
            prev.style.fontWeight = hasMot ? '600' : 'normal';
            prev.style.color = hasMot ? '' : 'var(--muted)';
            prev.style.fontStyle = hasMot ? '' : 'italic';
        }

        const btn = document.getElementById('btnEditBonoMotivo');
        if (btn) {
            btn.textContent = (bonoMotivo && bonoMotivo.trim()) ? 'Editar motivo' : 'Añadir motivo';
            btn.style.display = currentEstado === EST.PTE_PAGO ? '' : 'none';
        }
    }

    // ====== Anulación ======
    let cancelRequested = false;

    function cancelSetMsg(t, type = 'success') {
        const el = document.getElementById('cancelMsg');
        if (!el) return;
        el.textContent = t || '';
        el.className = 'alert ' + (type || 'success');
        el.style.display = t ? 'block' : 'none';
    }
    async function openCancelModal() {
        if (roleAdmin) {
            if (!confirm('¿Anular este ticket directamente?')) return;
            const r = await af(`/api/tickets/${currentId}/anular`, { method: 'POST' });
            if (!r.ok) {
                let t = ''; try { t = (await r.text())?.trim(); } catch { }
                setMsg(t || 'No se pudo anular el ticket', 'error');
                return;
            }
            currentEstado = EST.ANULADO;
            setStatus(EST.ANULADO);
            cancelRequested = true;
            updateCancelControls();
            setMsg('Ticket anulado', 'success');
            return;
        }
        cancelSetMsg('');
        const ta = document.getElementById('anulaMotivo');
        if (ta) { ta.value = ''; ta.focus(); }
        const cnt = document.getElementById('anulaMotivoCount');
        if (cnt) cnt.textContent = '0 / 300';
        document.getElementById('cancelModal')?.classList.add('open');
    }
    function closeCancelModal() {
        document.getElementById('cancelModal')?.classList.remove('open');
    }
    function updateCancelControls() {
        const box = document.getElementById('cancelBox');
        const badge = document.getElementById('cancelPendingBadge');
        const inScope = !!currentId && [EST.PTE_PAGO, EST.PAGADO].includes(currentEstado);
        if (box) box.style.display = (inScope && !cancelRequested) ? '' : 'none';
        if (badge) badge.style.display = (inScope && cancelRequested) ? '' : 'none';
    }

    // ===== Motivo de bono =====
    function setBonoMsg(t, type = 'success') {
        const el = document.getElementById('bonoMsg');
        if (!el) return;
        el.textContent = t || '';
        el.className = 'alert ' + (type || 'success');
        el.style.display = t ? 'block' : 'none';
    }
    function openBonoModal(forceFocus = false) {
        const modal = document.getElementById('bonoModal');
        const input = document.getElementById('bonoMotivoInput');
        if (!modal || !input) return;
        if (!currentId || currentEstado !== EST.PTE_PAGO || normalizeMetodoPago(document.getElementById('metodoPago')?.value) !== MP.BONO) {
            if (!forceFocus) setMsg('Disponible solo en tickets pendientes con método Bono', 'error');
            return;
        }
        setBonoMsg('');
        input.value = bonoMotivo || '';
        const cnt = document.getElementById('bonoMotivoCount');
        if (cnt) cnt.textContent = `${input.value.length} / 300`;
        modal.classList.add('open');
        setTimeout(() => input.focus(), 30);
    }
    function closeBonoModal() { document.getElementById('bonoModal')?.classList.remove('open'); }

    // ===== Guardar ticket =====
    async function guardarTicket() {
        if (savingTicket) return;
        if (currentId) { setMsg('Los tickets existentes no se pueden modificar', 'error'); return; }
        savingTicket = true;
        updateActionButtons();
        try {
            if (!vehiculoId.value) { setMsg('Selecciona un vehículo', 'error'); return; }
            if (!clienteId.value) { setMsg('Selecciona un cliente', 'error'); return; }

            if (!currentOperarioId) {
                await setOperarioFromMe();
                if (!currentOperarioId) { setMsg('No se pudo resolver el operario actual', 'error'); return; }
            }
            if (!tbodyLines.children.length) { setMsg('Añade al menos un servicio', 'error'); return; }

            const detalles = Array.from(tbodyLines.children).map(tr => {
                const servicioId = Number(tr.dataset.servicioId);
                const cantidad = Number(tr.querySelector('.qty')?.value || '1');
                const isOverride = tr.dataset.override === '1';

                const d = { servicioId, cantidad };
                if (isOverride) {
                    const p = Number(tr.dataset.price);
                    d.precio = Number.isFinite(p) ? Number(p.toFixed(2)) : 0;
                    d.descripcion = (tr.dataset.desc || '').trim();
                }
                return d;
            });

            const body = {
                fecha: new Date().toISOString(),
                comentarios: ($('#comentarios').value || '').trim() || null,
                estado: 'PTE_PAGO',
                metodoPago: null,
                usuarioId: Number(currentOperarioId),
                clienteId: Number($('#clienteId').value),
                vehiculoId: Number($('#vehiculoId').value),
                detalles
            };

            const r = await af('/api/tickets', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
                body: JSON.stringify(body)
            });

            if (!r.ok) { setMsg(await prettyHttpError(r, 'No se pudo guardar'), 'error'); return; }

            const saved = await r.json();
            currentId = saved.id;
            currentEstado = normalizeEstado(saved.estado || 'PTE_PAGO');
            $('#ticketId').value = currentId;
            history.replaceState(null, '', `/tickets/crear?id=${currentId}`);

            const clidNew = Number($('#clienteId').value);
            if (clidNew && !clienteNoDeseado) {
                try {
                    const rp = await af(`/api/clientes/${clidNew}/no-deseado/pendiente`, { headers: { 'Accept': 'application/json' } });
                    if (rp.ok) { const d = await rp.json(); ndRequested = !!d.pendiente; }
                } catch {}
            }

            setStatus(currentEstado);
            setMsg('Ticket creado · Marcado como PTE. DE PAGO', 'success');
            await loadDetalles(currentId);

            const mpSel = document.getElementById('metodoPago');
            if (mpSel && saved.metodoPago) mpSel.value = normalizeMetodoPago(saved.metodoPago);

            setEditable(false);
        } catch (e) {
            console.error(e);
            setMsg('Error de red al guardar', 'error');
        } finally {
            savingTicket = false;
            updateActionButtons();
        }
    }

    // ===== Eliminar =====
    function toggleDeleteBtn() {
        const btn = $('#btnEliminar');
        if (!btn) return;
        const hasId = !!$('#ticketId').value;
        btn.style.display = (roleAdmin && hasId) ? 'inline-block' : 'none';
    }

    // ===== Reset / Nuevo =====
    async function resetForm() {
        $('#formTicket').reset();

        $('#ticketId').value = '';
        vehiculoId.value = '';
        vehiculoSearch.value = '';
        resetClienteSelect();

        itemSearch.value = '';
        tbodyLines.innerHTML = '';
        $('#clientHistoryContainer').style.display = 'none';
        $('#fecha').value = toDateOnlyInput(new Date());

        document.getElementById('referencia').value = '';
        document.getElementById('referencia').placeholder = buildRefPlaceholder();

        bonoMotivo = '';
        cancelRequested = false;
        clienteNoDeseado = false;
        ndRequested = false;
        clienteNombre = '';

        currentId = null;
        currentEstado = EST.NUEVO;
        editMode = true;

        const mpSel = document.getElementById('metodoPago');
        if (mpSel) mpSel.value = '';

        setStatus(currentEstado);
        recompute();
        setMsg('');

        await setOperarioFromMe();
        setEditable(true);
        toggleDeleteBtn();
        await refreshNextReferencia();
        updateCancelControls();
    }

    // ===== Cargar ticket existente =====
    async function loadTicket(id) {
        sessionStorage.setItem('fromTicketsList', '1');
        const r = await af(`/api/tickets/${id}`, { headers: { 'Accept': 'application/json' } });
        if (!r.ok) { setMsg('No se pudo cargar el ticket', 'error'); return; }
        const t = await r.json();

        currentId = t.id;
        $('#ticketId').value = currentId;

        $('#fecha').value = toDateOnlyInput(t.fecha);
        $('#comentarios').value = t.comentarios || '';
        bonoMotivo = t.bonoMotivo || '';
        document.getElementById('referencia').value = t.referencia || '';

        currentEstado = normalizeEstado(t.estado || EST.PTE_PAGO);
        cancelRequested = false;
        setStatus(currentEstado);

        await setOperarioById(t.usuarioId || null);

        if (t.vehiculoId) {
            const rv = await af(`/api/vehiculos/${t.vehiculoId}`, { headers: { 'Accept': 'application/json' } });
            if (rv.ok) {
                const v = await rv.json();
                $('#vehiculoId').value = v.id;
                $('#vehiculoSearch').value = v.matricula || '';
            }
            await loadClientesDeVehiculo(t.vehiculoId, t.clienteId || null);
        } else {
            resetClienteSelect();
        }

        await loadDetalles(currentId);

        const mpSel = document.getElementById('metodoPago');
        if (mpSel && t.metodoPago) mpSel.value = normalizeMetodoPago(t.metodoPago);
        updatePayControls();

        clienteNoDeseado = false;
        ndRequested = false;
        if (t.clienteId) {
            const rc = await af(`/api/clientes/${t.clienteId}`, { headers: { 'Accept': 'application/json' } });
            if (rc.ok) {
                const cli = await rc.json();
                clienteNoDeseado = !!cli.noDeseado;
                clienteNombre = `${cli.nombre || ''}${cli.apellido ? ' ' + cli.apellido : ''}`.trim();
            }
            if (!clienteNoDeseado) {
                try {
                    const rp = await af(`/api/clientes/${t.clienteId}/no-deseado/pendiente`, { headers: { 'Accept': 'application/json' } });
                    if (rp.ok) { const d = await rp.json(); ndRequested = !!d.pendiente; }
                } catch {}
            }
        }
        updateNdButton();

        try {
            const rp = await af(`/api/tickets/${currentId}/anulacion/pendiente`, { headers: { 'Accept': 'application/json' } });
            if (rp.ok) { const d = await rp.json(); cancelRequested = !!d.pendiente; }
        } catch {}

        editMode = false;
        setEditable(false);
        updateActionButtons();
        toggleDeleteBtn();
        updateCancelControls();
    }

    // ===== NO deseado =====
    let clienteNoDeseado = false;
    let ndRequested = false;
    let clienteNombre = '';

    function updateNdButton() {
        const box = document.getElementById('ndBox');
        const badge = document.getElementById('ndPendingBadge');
        const cid = Number(clienteId.value) || 0;
        const selectedOpt = clienteId.options[clienteId.selectedIndex];
        const isNd = clienteNoDeseado || !!(selectedOpt?.dataset?.nd);
        const inScope = !!currentId && cid > 0 && [EST.PAGADO, EST.CERRADO].includes(currentEstado);
        if (box) box.style.display = (inScope && !isNd && !ndRequested) ? '' : 'none';
        if (badge) badge.style.display = (inScope && !isNd && ndRequested) ? '' : 'none';
    }

    const ndTicketModal = document.getElementById('ndTicketModal');
    const ndTicketMsg = document.getElementById('ndTicketMsg');
    const ndTicketForm = document.getElementById('ndTicketForm');

    function ndTicketSetMsg(t, type = 'success') {
        if (!ndTicketMsg) return;
        ndTicketMsg.textContent = t || '';
        ndTicketMsg.className = 'alert ' + type;
        ndTicketMsg.style.display = t ? 'block' : 'none';
    }
    async function openNdTicketModal() {
        const cid = Number(clienteId.value);
        if (!cid) { setMsg('No hay cliente seleccionado', 'error'); return; }
        if (clienteNoDeseado) { setMsg('El cliente ya está marcado como no deseado', 'error'); return; }

        if (roleAdmin) {
            const nombre = clienteNombre || `#${cid}`;
            if (!confirm(`¿Marcar a "${nombre}" como cliente no deseado?`)) return;
            const r = await af(`/api/clientes/${cid}/no-deseado/admin`, { method: 'POST' });
            if (!r.ok) {
                let t = ''; try { t = (await r.text())?.trim(); } catch { }
                setMsg(t || 'No se pudo marcar el cliente', 'error');
                return;
            }
            clienteNoDeseado = true;
            updateNdButton();
            setMsg('Cliente marcado como no deseado', 'success');
            return;
        }

        ndTicketSetMsg('');
        document.getElementById('ndTicketCliente').textContent = clienteNombre || `#${cid}`;
        const ta = document.getElementById('ndTicketMotivo');
        if (ta) ta.value = '';
        const cnt = document.getElementById('ndTicketMotivoCount');
        if (cnt) cnt.textContent = '0 / 300';
        ndTicketModal?.classList.add('open');
        setTimeout(() => ta?.focus(), 30);
    }
    function closeNdTicketModal() { ndTicketModal?.classList.remove('open'); }

    async function syncClienteNoDeseado(id) {
        if (!id) {
            clienteNoDeseado = false;
            clienteNombre = '';
            updateNdButton();
            return;
        }
        const rc = await af(`/api/clientes/${id}`, { headers: { 'Accept': 'application/json' } });
        if (rc.ok) {
            const cli = await rc.json();
            clienteNoDeseado = !!cli.noDeseado;
            clienteNombre = `${cli.nombre || ''}${cli.apellido ? ' ' + cli.apellido : ''}`.trim();
            updateNdButton();
        }
    }

    // ===== Modal: Nuevo vehículo =====
    const vehModal = document.querySelector('#vehModal');
    const vehMsg = document.querySelector('#vehMsg');
    const vehForm = document.querySelector('#vehForm');
    const vehClose = document.querySelector('#vehClose');
    const vehCancel = document.querySelector('#vehCancel');
    const btnNuevoVeh = document.querySelector('#btnNuevoVeh');

    const mvMatricula = document.querySelector('#mvMatricula');
    const mvMarca = document.querySelector('#mvMarca');
    const mvModelo = document.querySelector('#mvModelo');
    const mvColor = document.querySelector('#mvColor');
    const mvPlaza = document.querySelector('#mvPlaza');

    const mvClienteSearch = document.querySelector('#mvClienteSearch');
    const mvClienteId = document.querySelector('#mvClienteId');
    const mvClienteList = document.querySelector('#mvClienteList');

    const mvNewClientToggle = document.querySelector('#mvNewClientToggle');
    const newClientBox = document.querySelector('#newClientBox');
    const mvCliNombre = document.querySelector('#mvCliNombre');
    const mvCliApellido = document.querySelector('#mvCliApellido');
    const mvCliNif = document.querySelector('#mvCliNif');
    const mvCliCorreo = document.querySelector('#mvCliCorreo');
    const mvCliTelefono = document.querySelector('#mvCliTelefono');

    const mvRequiredNewClientFields = [mvCliNombre, mvCliApellido, mvCliTelefono];
    const phoneRegex = /^[0-9+\-().\s]{3,30}$/;

    function updateNewClientRequired(req) {
        mvRequiredNewClientFields.forEach(f => { if (f) f.required = !!req; });
    }
    function isPhoneValid(raw) {
        const val = (raw || '').trim();
        const digits = val.replace(/\D/g, '');
        return !!val && phoneRegex.test(val) && digits.length >= 6;
    }
    function vehSetMsg(t, type = 'success') {
        vehMsg.textContent = t || '';
        vehMsg.className = 'alert ' + type;
        vehMsg.style.display = t ? 'block' : 'none';
    }
    function openVehModal() {
        if (currentId) { setMsg('Los tickets existentes no se pueden modificar', 'error'); return; }
        vehSetMsg('');
        vehForm.reset();
        mvClienteId.value = '';
        mvClienteList.style.display = 'none';
        newClientBox.style.display = 'none';
        mvNewClientToggle.checked = false;
        updateNewClientRequired(false);
        mvMatricula.value = vehiculoId.value ? '' : (vehiculoSearch.value || '').trim().toUpperCase();
        vehModal.classList.add('open');
    }
    function closeVehModal() { vehModal.classList.remove('open'); }

    let mvCliItems = [], mvCliTimer = null;

    async function mvSearchClientes(term) {
        term = (term || '').trim();
        if (term.length < 2) { mvClienteList.style.display = 'none'; return; }

        const qs = new URLSearchParams({ q: term, page: 0, size: 8 });
        const r = await af('/api/clientes?' + qs.toString(), { headers: { 'Accept': 'application/json' } });

        if (!r.ok) {
            mvClienteList.innerHTML = '<div class="nores">Error cargando</div>';
            mvClienteList.style.display = 'block';
            return;
        }

        const data = await r.json();
        const list = data.content || data || [];
        mvCliItems = list;

        if (!list.length) {
            mvClienteList.innerHTML = '<div class="nores">Sin resultados</div>';
            mvClienteList.style.display = 'block';
            return;
        }

        mvClienteList.innerHTML = list.map((c, i) => {
            const full = `${c.nombre || ''}${c.apellido ? ' ' + c.apellido : ''}`.trim();
            const fullHighlighted = highlightMatches(full, term);
            const label = c.noDeseado
                ? `<span class="nd">${fullHighlighted}</span> <span class="badge-nd">NO DESEADO</span>`
                : fullHighlighted;
            const nif = c.nif ? highlightMatches(c.nif, term) : '';
            const telefono = c.telefono ? highlightMatches(c.telefono, term) : '';
            const metaParts = [nif, telefono].filter(Boolean);
            const meta = metaParts.length ? ` · ${metaParts.join(' · ')}` : '';
            return `<div class="autocomplete-item" data-i="${i}">${label}${meta}</div>`;
        }).join('');
        mvClienteList.style.display = 'block';
    }

    function mvSelectCliente(i) {
        const c = mvCliItems[i];
        if (!c) return;
        const full = `${c.nombre || ''}${c.apellido ? ' ' + c.apellido : ''}`.trim();
        mvClienteSearch.value = full;
        mvClienteId.value = c.id;
        mvClienteList.style.display = 'none';
    }

    // ===== MODAL: Añadir cliente a vehículo existente =====
    const cliModal = document.getElementById('cliModal');
    const cliMsg = document.getElementById('cliMsg');
    const cliForm = document.getElementById('cliForm');
    const cliClose = document.getElementById('cliClose');
    const cliCancel = document.getElementById('cliCancel');

    const cliNombre = document.getElementById('mcNombre');
    const cliApellido = document.getElementById('mcApellido');
    const cliNif = document.getElementById('mcNif');
    const cliCorreo = document.getElementById('mcCorreo');
    const cliTelefono = document.getElementById('mcTelefono');

    function cliSetMsg(t, type = 'success') {
        if (!cliMsg) return;
        cliMsg.textContent = t || '';
        cliMsg.className = 'alert ' + type;
        cliMsg.style.display = t ? 'block' : 'none';
    }
    function openCliModal() {
        if (!cliModal) { setMsg('Falta el modal de cliente (cliModal)', 'error'); clienteId.value = prevClienteSelection || ''; return; }
        if (currentId) { setMsg('Los tickets existentes no se pueden modificar', 'error'); clienteId.value = prevClienteSelection || ''; return; }
        if (!vehiculoId.value) { setMsg('Selecciona un vehículo primero', 'error'); clienteId.value = prevClienteSelection || ''; return; }

        cliSetMsg('');
        cliForm?.reset?.();
        cliModal.classList.add('open');
        setTimeout(() => cliNombre?.focus?.(), 30);
    }
    function closeCliModal(restoreSelection = true) {
        cliModal?.classList.remove('open');
        if (restoreSelection) clienteId.value = prevClienteSelection || '';
    }

    // ===== Líneas editables (modal) =====
    const editableModal = document.getElementById('editableModal');
    const editableTbody = document.getElementById('editableTbody');
    const editableMsg = document.getElementById('editableMsg');
    const editableSearch = document.getElementById('editableSearch');

    function editableSetMsg(t, type = 'success') {
        if (!editableMsg) return;
        editableMsg.textContent = t || '';
        editableMsg.className = 'alert ' + type;
        editableMsg.style.display = t ? 'block' : 'none';
    }

    let editableItems = [];
    function openEditableModal() {
        if (currentId) { setMsg('Los tickets existentes no se pueden modificar', 'error'); return; }
        editableSetMsg('');
        if (editableSearch) editableSearch.value = '';
        editableModal?.classList.add('open');
        loadEditableItems('');
    }
    function closeEditableModal() { editableModal?.classList.remove('open'); }

    let editableTimer = null;

    async function loadEditableItems(q) {
        const centroId = me?.centroId ?? me?.centroID ?? me?.centro_id;
        if (!centroId) { editableSetMsg('Tu usuario no tiene centro asignado', 'error'); return; }

        try {
            const params = new URLSearchParams({
                centroId: String(centroId),
                editable: 'true',
                q: (q || '').trim()
            });

            const r = await af('/api/servicios?' + params.toString(), { headers: { 'Accept': 'application/json' } });
            if (!r.ok) { editableSetMsg('No se pudieron cargar los editables', 'error'); editableTbody.innerHTML = ''; return; }

            const list = await r.json();
            editableItems = Array.isArray(list) ? list : [];

            if (!editableItems.length) { editableTbody.innerHTML = `<tr><td colspan="4" class="muted">Sin resultados</td></tr>`; return; }

            editableTbody.innerHTML = editableItems.map((it, i) => {
                const desc = escapeHtml(it.descripcion || '');
                const price = Number(it.importe ?? 0);
                return `
<tr data-i="${i}">
    <td><input class="edDesc" value="${desc}" maxlength="255" /></td>
    <td class="right">
    <input class="edPrice" type="number" step="0.01" min="0" value="${price.toFixed(2)}" style="width:140px; text-align:right;" />
    </td>
    <td class="right"><button type="button" class="secondary edAdd">Añadir</button></td>
</tr>
`;
            }).join('');
        } catch {
            editableSetMsg('Error de red cargando editables', 'error');
        }
    }

    // ===== Init =====
    async function init() {
        // ---- Autocomplete vehículo ----
        vehiculoSearch.addEventListener('input', () => {
            $('#clientHistoryContainer').style.display = 'none';
            if (vehiculoId.value) {
                vehiculoId.value = '';
                resetClienteSelect();
            }
            clearTimeout(vehTimer);
            vehTimer = setTimeout(() => searchVehiculos(vehiculoSearch.value), 250);
        });
        vehiculoSearch.addEventListener('focus', () => {
            $('#clientHistoryContainer').style.display = 'none';
            if (vehiculoId.value) {
                vehiculoId.value = '';
                vehiculoSearch.value = '';
                resetClienteSelect();
            }
            if ((vehiculoSearch.value || '').trim().length >= 2) searchVehiculos(vehiculoSearch.value);
        });
        vehiculoList.addEventListener('mousedown', e => {
            const it = e.target.closest('.autocomplete-item');
            if (it) selectVehiculoByIndex(parseInt(it.dataset.i, 10));
        });
        vehiculoSearch.addEventListener('blur', () => { vehiculoList.style.display = 'none'; });
        document.addEventListener('click', e => {
            if (!e.target.closest('.autocomplete')) vehiculoList.style.display = 'none';
        });

        // ---- Autocomplete items ----
        itemSearch.addEventListener('input', () => { clearTimeout(itemTimer); itemTimer = setTimeout(() => searchItems(itemSearch.value), 250); });
        itemSearch.addEventListener('focus', () => { if ((itemSearch.value || '').trim().length >= 1) searchItems(itemSearch.value); });
        itemSearch.addEventListener('blur', () => { itemList.style.display = 'none'; });
        itemList.addEventListener('mousedown', async (e) => {
            const it = e.target.closest('.autocomplete-item');
            if (!it) return;
            const idx = parseInt(it.dataset.i, 10), item = itemItems[idx];
            itemList.style.display = 'none';
            itemSearch.value = '';

            if (currentId) { setMsg('Los tickets existentes no se pueden modificar', 'error'); return; }

            addLine({
                id: item.id,
                descripcion: item.descripcion,
                precio: item.importe,
                precioTicket: item.importeCeroEnTicket ? 0 : item.importe,
                cantidad: 1,
                importeCeroEnTicket: item.importeCeroEnTicket,
                override: false
            }, false);
            recompute();
        });

        // ---- Líneas ----
        tbodyLines.addEventListener('input', async (e) => {
            if (!e.target.classList.contains('qty')) return;
            if (currentId) { setMsg('Los tickets existentes no se pueden modificar', 'error'); return; }
            recompute();
        });
        tbodyLines.addEventListener('click', async (e) => {
            if (!e.target.classList.contains('del')) return;
            if (currentId) { setMsg('Los tickets existentes no se pueden modificar', 'error'); return; }
            const tr = e.target.closest('tr');
            if (tr) { tr.remove(); recompute(); }
        });

        // ---- Método de pago ----
        document.getElementById('metodoPago')?.addEventListener('change', () => updateBonoControls());

        // ---- Modal anulación ----
        document.getElementById('btnSolicitarAnulacion')?.addEventListener('click', openCancelModal);
        document.getElementById('cancelClose')?.addEventListener('click', closeCancelModal);
        document.getElementById('cancelAbort')?.addEventListener('click', closeCancelModal);
        document.querySelector('#cancelModal .modal-backdrop')?.addEventListener('click', closeCancelModal);
        document.getElementById('cancelForm')?.addEventListener('submit', async (ev) => {
            ev.preventDefault();
            if (!currentId) { cancelSetMsg('No hay ticket cargado', 'error'); return; }
            if (![EST.PTE_PAGO, EST.PAGADO].includes(currentEstado)) { cancelSetMsg('Solo disponible en estados Pte. de pago o Pagado', 'error'); return; }

            const motivo = (document.getElementById('anulaMotivo')?.value || '').trim();
            if (!motivo) { cancelSetMsg('El motivo es obligatorio', 'error'); return; }

            const btn = ev.target.querySelector('[type="submit"]');
            if (btn) { btn.disabled = true; btn.textContent = 'Enviando...'; }
            try {
                const r = await af(`/api/tickets/${currentId}/anulacion/solicitar`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
                    body: JSON.stringify({ motivo })
                });
                if (!r.ok) {
                    let t = ''; try { t = (await r.text())?.trim(); } catch { }
                    cancelSetMsg(t || 'No se pudo enviar la solicitud', 'error');
                    return;
                }
                cancelRequested = true;
                setMsg('Solicitud de anulación enviada. Se notificará al administrador por correo electrónico.', 'success');
                updateCancelControls();
                closeCancelModal();
            } catch {
                cancelSetMsg('Error de red al enviar la solicitud', 'error');
            } finally {
                if (btn) { btn.disabled = false; btn.textContent = 'Enviar solicitud'; }
            }
        });

        // ---- Modal bono ----
        document.getElementById('btnEditBonoMotivo')?.addEventListener('click', () => openBonoModal(false));
        document.getElementById('bonoClose')?.addEventListener('click', closeBonoModal);
        document.getElementById('bonoCancel')?.addEventListener('click', closeBonoModal);
        document.querySelector('#bonoModal .modal-backdrop')?.addEventListener('click', closeBonoModal);
        document.getElementById('bonoForm')?.addEventListener('submit', async (ev) => {
            ev.preventDefault();
            if (!currentId) { setBonoMsg('Guarda el ticket antes de añadir el motivo', 'error'); return; }

            const motivo = (document.getElementById('bonoMotivoInput')?.value || '').trim();
            if (!motivo) { setBonoMsg('El motivo es obligatorio', 'error'); return; }

            try {
                const r = await af(`/api/tickets/${currentId}`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
                    body: JSON.stringify({ bonoMotivo: motivo })
                });
                if (!r.ok) {
                    let t = ''; try { t = (await r.text())?.trim(); } catch { }
                    setBonoMsg(t || 'No se pudo guardar el motivo', 'error');
                    return;
                }
                bonoMotivo = motivo;
                updateBonoControls();
                closeBonoModal();
                setMsg('Motivo de bono guardado', 'success');
            } catch {
                setBonoMsg('Error de red al guardar motivo', 'error');
            }
        });

        // ---- Contadores de caracteres en textareas de modales ----
        document.getElementById('anulaMotivo')?.addEventListener('input', e => {
            const cnt = document.getElementById('anulaMotivoCount');
            if (cnt) cnt.textContent = `${e.target.value.length} / 300`;
        });
        document.getElementById('bonoMotivoInput')?.addEventListener('input', e => {
            const cnt = document.getElementById('bonoMotivoCount');
            if (cnt) cnt.textContent = `${e.target.value.length} / 300`;
        });
        document.getElementById('ndTicketMotivo')?.addEventListener('input', e => {
            const cnt = document.getElementById('ndTicketMotivoCount');
            if (cnt) cnt.textContent = `${e.target.value.length} / 300`;
        });

        // ---- Imprimir ----
        document.getElementById('btnImprimir')?.addEventListener('click', () => {
            if (!currentId) return;
            window.location.href = `/pdf_viewer.html?kind=tickets&id=${encodeURIComponent(currentId)}`;
        });

        // ---- Eliminar / Nuevo / Guardar ----
        $('#btnEliminar')?.addEventListener('click', async () => {
            if (!currentId) return;
            if (!confirm(`¿Eliminar el ticket ${currentId}?`)) return;
            const r = await af(`/api/tickets/${currentId}`, { method: 'DELETE' });
            if (r.status === 204) { setMsg('Ticket eliminado', 'success'); resetForm(); }
            else if (r.status === 403) setMsg('Solo ADMIN puede eliminar', 'error');
            else if (r.status === 404) setMsg('Ticket no encontrado', 'error');
            else setMsg('No se pudo eliminar', 'error');
        });
        $('#btnNuevo')?.addEventListener('click', resetForm);
        $('#btnGuardar')?.addEventListener('click', guardarTicket);

        // ---- Convertir a Pagado / Entregado ----
        document.getElementById('btnConvertirPagado')?.addEventListener('click', async () => {
            if (!currentId) return;
            if (currentEstado !== EST.PTE_PAGO) { setMsg('Solo disponible en estado Pte. de pago', 'error'); return; }

            const mpSel = document.getElementById('metodoPago');
            const mp = mpSel ? mpSel.value : '';
            if (!mp) { setMsg('Selecciona el método de pago', 'error'); return; }

            if (normalizeMetodoPago(mp) === MP.BONO) {
                if (!bonoMotivo || !bonoMotivo.trim()) { setMsg('Añade el motivo del bono antes de marcar como pagado', 'error'); return; }
            }

            if (!confirm('¿Pasar este ticket a pagado?')) return;

            const r = await af(`/api/tickets/${currentId}/pagar`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
                body: JSON.stringify({ metodoPago: mp, bonoMotivo: bonoMotivo || null })
            });

            if (!r.ok) {
                let t = ''; try { t = (await r.text())?.trim(); } catch { }
                setMsg(t || 'No se pudo marcar el ticket como pagado', 'error');
                return;
            }

            const saved = await r.json();
            setStatus(normalizeEstado(saved.estado || EST.PAGADO));
            if (mpSel && saved.metodoPago) mpSel.value = normalizeMetodoPago(saved.metodoPago);
            bonoMotivo = saved.bonoMotivo || '';
            updatePayControls();
            setMsg('Ticket marcado como PAGADO', 'success');
            setEditable(false);
        });

        document.getElementById('btnConvertirEntregado')?.addEventListener('click', async () => {
            if (!currentId) return;
            if (currentEstado !== EST.PAGADO) { setMsg('Solo disponible cuando el ticket está PAGADO', 'error'); return; }
            if (!confirm('¿Pasar este ticket a cerrado?')) return;

            const r = await af(`/api/tickets/${currentId}/entregar`, { method: 'POST', headers: { 'Accept': 'application/json' } });

            if (!r.ok) {
                let t = ''; try { t = (await r.text())?.trim(); } catch { }
                setMsg(t || 'No se pudo marcar el ticket como cerrado', 'error');
                return;
            }

            const saved = await r.json();
            setStatus(saved.estado || EST.CERRADO);
            setMsg('Ticket marcado como CERRADO', 'success');
        });

        // ---- Cliente select ----
        clienteId.addEventListener('focus', () => {
            const v = clienteId.value;
            prevClienteSelection = (v && v !== '__NEW_CLIENT__') ? v : '';
        });
        clienteId.addEventListener('change', async () => {
            const val = clienteId.value;
            if (val === '__NEW_CLIENT__') {
                if (currentId) { setMsg('Los tickets existentes no se pueden modificar', 'error'); clienteId.value = prevClienteSelection || ''; return; }
                if (!vehiculoId.value) { setMsg('Selecciona un vehículo primero', 'error'); clienteId.value = ''; return; }
                openCliModal();
                return;
            }
            loadClientHistory(val);
            await syncClienteNoDeseado(val);
        });

        // ---- Modal nuevo vehículo ----
        btnNuevoVeh?.addEventListener('click', openVehModal);
        vehCancel?.addEventListener('click', closeVehModal);
        vehClose?.addEventListener('click', closeVehModal);
        vehModal?.querySelector('.modal-backdrop')?.addEventListener('click', closeVehModal);
        mvNewClientToggle?.addEventListener('change', () => {
            newClientBox.style.display = mvNewClientToggle.checked ? 'grid' : 'none';
            updateNewClientRequired(mvNewClientToggle.checked);
            if (mvNewClientToggle.checked) {
                mvClienteId.value = '';
                mvClienteSearch.value = '';
                mvClienteList.style.display = 'none';
            }
        });
        mvClienteSearch?.addEventListener('input', () => {
            mvClienteId.value = '';
            clearTimeout(mvCliTimer);
            mvCliTimer = setTimeout(() => mvSearchClientes(mvClienteSearch.value), 250);
        });
        mvClienteList?.addEventListener('mousedown', e => {
            const it = e.target.closest('.autocomplete-item');
            if (!it) return;
            mvSelectCliente(parseInt(it.dataset.i, 10));
        });
        mvClienteSearch?.addEventListener('focus', () => { if ((mvClienteSearch.value || '').trim().length >= 2) mvSearchClientes(mvClienteSearch.value); });
        mvClienteSearch?.addEventListener('blur', () => { mvClienteList.style.display = 'none'; });
        document.addEventListener('click', e => {
            if (!e.target.closest('#vehModal .autocomplete')) mvClienteList.style.display = 'none';
        });
        vehForm?.addEventListener('submit', async (ev) => {
            ev.preventDefault();
            if (currentId) { vehSetMsg('No se puede modificar un ticket existente', 'error'); return; }

            vehSetMsg('Guardando…');
            const matricula = (mvMatricula.value || '').trim().toUpperCase();
            if (!matricula) { vehSetMsg('La matrícula es obligatoria', 'error'); return; }

            let cId = null;

            if (mvNewClientToggle.checked) {
                const nombre = (mvCliNombre.value || '').trim();
                const apellido = (mvCliApellido.value || '').trim();
                if (!nombre || !apellido) { vehSetMsg('Nombre y apellido del cliente son obligatorios', 'error'); return; }
                if (!isPhoneValid(mvCliTelefono.value)) {
                    vehSetMsg('Teléfono inválido. Usa solo dígitos y símbolos + - ( ) . con al menos 6 números.', 'error');
                    mvCliTelefono.focus();
                    return;
                }

                const cBody = {
                    nombre, apellido,
                    telefono: (mvCliTelefono.value || '').trim() || null,
                    correo: (mvCliCorreo.value || '').trim() || null,
                    nif: (mvCliNif.value || '').trim() || null,
                    direccion: null, codigoPostal: null,
                    noDeseado: false
                };

                const rc = await af('/api/clientes', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
                    body: JSON.stringify(cBody)
                });

                if (!rc.ok) { vehSetMsg(await prettyHttpError(rc, 'No se pudo crear el cliente'), 'error'); return; }
                const cNew = await rc.json();
                cId = cNew.id;
            } else {
                if (!mvClienteId.value) { vehSetMsg('Selecciona un cliente existente o marca "Crear nuevo cliente"', 'error'); return; }
                cId = Number(mvClienteId.value);
            }

            const vBody = {
                matricula,
                marca: (mvMarca.value || '').trim() || null,
                modelo: (mvModelo.value || '').trim() || null,
                color: (mvColor.value || '').trim() || null,
                plaza: (mvPlaza.value || '').trim() || null
            };

            const rv = await af('/api/vehiculos', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
                body: JSON.stringify(vBody)
            });

            if (!rv.ok) { vehSetMsg((await rv.text()) || 'No se pudo crear el vehículo', 'error'); return; }

            const vNew = await rv.json();
            const ra = await af(`/api/vehiculos/${vNew.id}/clientes/${cId}`, { method: 'POST' });

            if (!ra.ok) { vehSetMsg((await ra.text()) || 'No se pudo asociar el cliente al vehículo', 'error'); return; }

            prevClienteSelection = '';
            $('#vehiculoId').value = vNew.id;
            $('#vehiculoSearch').value = vNew.matricula;

            await loadClientesDeVehiculo(vNew.id, cId);

            vehSetMsg('');
            vehModal.classList.remove('open');
        });

        // ---- Modal añadir cliente a vehículo existente ----
        cliClose?.addEventListener('click', () => closeCliModal(true));
        cliCancel?.addEventListener('click', () => closeCliModal(true));
        cliModal?.querySelector('.modal-backdrop')?.addEventListener('click', () => closeCliModal(true));
        cliForm?.addEventListener('submit', async (ev) => {
            ev.preventDefault();
            if (currentId) { cliSetMsg('No se puede modificar un ticket existente', 'error'); return; }
            const vId = Number(vehiculoId.value || 0);
            if (!vId) { cliSetMsg('No hay vehículo seleccionado', 'error'); return; }

            const nombre = (cliNombre?.value || '').trim();
            const apellido = (cliApellido?.value || '').trim();
            const telefono = (cliTelefono?.value || '').trim();

            if (!nombre || !apellido) { cliSetMsg('Nombre y apellido son obligatorios', 'error'); return; }
            if (!isPhoneValid(telefono)) { cliSetMsg('Teléfono inválido (mín. 6 dígitos)', 'error'); cliTelefono?.focus?.(); return; }

            cliSetMsg('Guardando…');

            try {
                const cBody = {
                    nombre,
                    apellido,
                    telefono: telefono || null,
                    correo: (cliCorreo?.value || '').trim() || null,
                    nif: (cliNif?.value || '').trim() || null,
                    direccion: null,
                    codigoPostal: null,
                    noDeseado: false
                };

                const rc = await af('/api/clientes', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
                    body: JSON.stringify(cBody)
                });

                if (!rc.ok) { cliSetMsg(await prettyHttpError(rc, 'No se pudo crear el cliente'), 'error'); return; }
                const cNew = await rc.json();

                const ra = await af(`/api/vehiculos/${vId}/clientes/${cNew.id}`, { method: 'POST' });
                if (!ra.ok) { cliSetMsg((await ra.text()) || 'No se pudo asociar el cliente al vehículo', 'error'); return; }

                prevClienteSelection = String(cNew.id);
                await loadClientesDeVehiculo(vId, cNew.id);

                clienteNoDeseado = false;
                clienteNombre = `${nombre}${apellido ? ' ' + apellido : ''}`.trim();

                cliSetMsg('');
                closeCliModal(false);
            } catch {
                cliSetMsg('Error de red guardando cliente', 'error');
            }
        });

        // ---- Modal líneas editables ----
        document.getElementById('btnAddEditable')?.addEventListener('click', openEditableModal);
        document.getElementById('editableClose')?.addEventListener('click', closeEditableModal);
        editableModal?.querySelector('.modal-backdrop')?.addEventListener('click', closeEditableModal);
        editableSearch?.addEventListener('input', () => {
            clearTimeout(editableTimer);
            editableTimer = setTimeout(() => loadEditableItems(editableSearch.value || ''), 250);
        });
        editableTbody?.addEventListener('click', (e) => {
            const btn = e.target.closest('.edAdd');
            if (!btn) return;
            if (currentId) { setMsg('Los tickets existentes no se pueden modificar', 'error'); return; }

            const tr = btn.closest('tr');
            const idx = Number(tr?.dataset?.i);
            const item = editableItems[idx];
            if (!item) return;

            const desc = (tr.querySelector('.edDesc')?.value || '').trim();
            const price = Number(tr.querySelector('.edPrice')?.value || '0');

            if (!desc) { editableSetMsg('La descripción es obligatoria', 'error'); return; }
            if (!Number.isFinite(price) || price < 0) { editableSetMsg('Importe inválido', 'error'); return; }

            addLine({
                id: item.id,
                descripcion: desc,
                precio: price,
                precioTicket: item.importeCeroEnTicket ? 0 : price,
                cantidad: 1,
                importeCeroEnTicket: !!item.importeCeroEnTicket,
                override: true
            }, false);

            recompute();
            closeEditableModal();
        });

        // ---- Modal no deseado (desde ticket) ----
        document.getElementById('btnMarcarNoDeseado')?.addEventListener('click', openNdTicketModal);
        document.getElementById('ndTicketClose')?.addEventListener('click', closeNdTicketModal);
        document.getElementById('ndTicketCancel')?.addEventListener('click', closeNdTicketModal);
        ndTicketModal?.querySelector('.modal-backdrop')?.addEventListener('click', closeNdTicketModal);
        ndTicketForm?.addEventListener('submit', async (ev) => {
            ev.preventDefault();
            const cid = Number(clienteId.value);
            if (!cid) { ndTicketSetMsg('No hay cliente seleccionado', 'error'); return; }
            const motivo = (document.getElementById('ndTicketMotivo')?.value || '').trim();
            if (!motivo) { ndTicketSetMsg('El motivo es obligatorio', 'error'); return; }
            const btn = ev.target.querySelector('[type="submit"]');
            if (btn) { btn.disabled = true; btn.textContent = 'Enviando…'; }
            try {
                const r = await af(`/api/clientes/${cid}/no-deseado/solicitar`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
                    body: JSON.stringify({ motivo })
                });
                if (!r.ok) {
                    let t = ''; try { t = (await r.text())?.trim(); } catch { }
                    ndTicketSetMsg(t || 'No se pudo enviar la solicitud', 'error');
                    return;
                }
                ndRequested = true;
                updateNdButton();
                closeNdTicketModal();
                setMsg('Solicitud de cliente No deseado enviada. Se notificará al administrador por correo electrónico.', 'success');
            } catch {
                ndTicketSetMsg('Error de red al enviar la solicitud', 'error');
            } finally {
                if (btn) { btn.disabled = false; btn.textContent = 'Enviar solicitud'; }
            }
        });

        // ---- Arranque ----
        await whoAmI();
        resetClienteSelect();
        const qId = getParam('id');
        if (qId) await loadTicket(qId);
        else await resetForm();
        document.querySelector('main').style.opacity = '1';
    }

    init();
})();
