(function () {
    const af = window.authFetch;
    const escapeHtml = window.escapeHtml;

    let currentPage = 0;
    const pageSize = 10000;
    let IS_ADMIN = false;

    const $ = s => document.querySelector(s);
    const pendientesCard = $('#pendientesCard');
    const STORAGE_KEY = 'informes.filters';
    const RESTORE_KEY = 'informes.restore';
    const fmtEur = n => (Number(n || 0)).toFixed(2).replace('.', ',') + ' €';
    const METODO_LABEL = { EFECTIVO: 'Efectivo', TARJETA: 'Tarjeta', BIZUM: 'Bizum', BONO: 'Bono', TRANSFERENCIA: 'Transferencia', OTRO: 'Otro' };

    // "1.234,56 €" -> 1234.56
    function parseEurText(el) {
        const t = (el?.textContent || '0').replace(/\s|€/g, '').replace(/\./g, '').replace(',', '.');
        const n = Number(t);
        return Number.isFinite(n) ? n : 0;
    }

    // ===== Guard: sesión requerida =====
    async function ensureAuthenticated() {
        const r = await af('/auth/me', { headers: { 'Accept': 'application/json' } });
        if (!r.ok) { window.location.href = '/login.html'; return null; }
        const me = await r.json();
        $('#username').textContent = me.usuario || '—';
        $('#centro').textContent = me.centroTrabajo || '—';
        return me;
    }

    // ===== Fechas helpers =====
    const pad = n => String(n).padStart(2, '0');
    const toDateStr = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
    function setRangeMesActual() {
        const now = new Date();
        const desde = new Date(now.getFullYear(), now.getMonth(), 1);
        const hasta = now;
        $('#fdesde').value = toDateStr(desde);
        $('#fhasta').value = toDateStr(hasta);
    }
    function setRangeUlt7() {
        const hasta = new Date();
        const desde = new Date(); desde.setDate(hasta.getDate() - 6);
        $('#fdesde').value = toDateStr(desde);
        $('#fhasta').value = toDateStr(hasta);
    }
    function setRangeHoy() {
        const d = new Date();
        const s = toDateStr(d);
        $('#fdesde').value = s; $('#fhasta').value = s;
    }

    // ===== Centros =====
    async function loadCentros(me) {
        const sel = $('#centroId');
        const help = $('#centroHelp');
        if (!sel) return;
        try {
            const r = await af('/api/centros', { headers: { 'Accept': 'application/json' } });
            if (!r.ok) { if (help) help.textContent = 'No se pudieron cargar los centros'; return; }
            const data = await r.json();
            const list = Array.isArray(data) ? data : (data.content || []);

            const centroUserId = me?.centroId;

            if (!IS_ADMIN && centroUserId != null) {
                const centroUser = list.find(c => String(c.id) === String(centroUserId));
                const label = centroUser ? (centroUser.nombre || ('Centro ' + centroUser.id)) : ('Centro ' + centroUserId);
                sel.innerHTML = `<option value="${centroUserId}">${escapeHtml(label)}</option>`;
                sel.value = String(centroUserId);
                sel.disabled = true;
                if (help) help.textContent = '';
                sel.title = 'Solo puedes consultar tu centro de trabajo';
                return;
            }

            sel.innerHTML = `<option value="">Todos los centros</option>` +
                list.map(c => `<option value="${c.id}">${escapeHtml(c.nombre || ('Centro ' + c.id))}</option>`).join('');
            if (help) help.textContent = '';
        } catch {
            if (help) help.textContent = 'No se pudieron cargar los centros';
        }
    }

    // ===== KPIs =====
    function renderKpis(d) {
        const k = d.kpis || d.resumen || d || {};
        let totalTickets = k.totalTickets;
        if (totalTickets == null) {
            const byDay = d.byDay || d.dias || d.porDia || [];
            totalTickets = Array.isArray(byDay)
                ? byDay.reduce((s, r) => s + Number(r.tickets != null ? r.tickets : (r.count != null ? r.count : 0)), 0)
                : (k.tickets != null ? k.tickets : 0);
        }
        let base = (k.totalBase != null ? k.totalBase : (k.base != null ? k.base : k.totalSinIva));
        let total = (k.totalConIva != null ? k.totalConIva : k.total);
        if (base == null || total == null) {
            const byDay = d.byDay || d.dias || d.porDia || [];
            if (Array.isArray(byDay) && byDay.length) {
                base = byDay.reduce((s, r) => s + Number(r.base != null ? r.base : (r.totalSinIva != null ? r.totalSinIva : 0)), 0);
                total = byDay.reduce((s, r) => s + Number(r.total != null ? r.total : (r.totalConIva != null ? r.totalConIva : 0)), 0);
            } else {
                base = Number(base || 0);
                total = Number(total || 0);
            }
        }
        base = Number(base || 0);
        total = Number(total || 0);
        const iva = (k.totalIva != null ? Number(k.totalIva) : (k.iva != null ? Number(k.iva) : (total - base)));

        const kTicketsEl = document.getElementById('k_tickets');
        const kBaseEl = document.getElementById('k_base');
        const kIvaEl = document.getElementById('k_iva');
        const kTotalEl = document.getElementById('k_total');

        if (kTicketsEl) kTicketsEl.textContent = String(totalTickets);
        if (kBaseEl) kBaseEl.textContent = fmtEur(base);
        if (kIvaEl) kIvaEl.textContent = fmtEur(iva);
        if (kTotalEl) kTotalEl.textContent = fmtEur(total);
    }

    // ===== Adelantos + Neto =====
    async function paintAdelantosYNeto(qs) {
        try {
            const r = await af('/api/informes/resumen-con-adelantos?' + qs.toString(), { headers: { 'Accept': 'application/json' } });
            if (!r.ok) return;
            const data = await r.json();
            const elAd = document.getElementById('k_adelantos');
            const elNt = document.getElementById('k_neto');
            if (elAd) elAd.textContent = fmtEur(data.adelantosAceptados ?? 0);
            if (elNt) elNt.textContent = fmtEur(data.beneficioNeto ?? 0);
        } catch { }
    }

    // ===== Resumen (top) =====
    async function loadResumen() {
        const desde = ($('#fdesde').value || '').trim();
        const hasta = ($('#fhasta').value || '').trim();
        if (!desde || !hasta) { setMsg('Selecciona el rango de fechas', 'error'); return; }
        const centroId = ($('#centroId') && $('#centroId').value) ? $('#centroId').value.trim() : '';
        const metodo = ($('#metodo') && $('#metodo').value) ? $('#metodo').value.trim() : '';

        const qs = new URLSearchParams();
        qs.set('from', desde); qs.set('to', hasta);
        qs.set('desde', desde); qs.set('hasta', hasta);
        if (centroId) qs.set('centroId', centroId);
        if (metodo) qs.set('metodo', metodo);

        try {
            const r = await af('/api/informes/resumen?' + qs.toString(), { headers: { 'Accept': 'application/json' } });
            if (!r.ok) { let t = ''; try { t = (await r.text())?.trim(); } catch { } setMsg(t || 'No se pudo cargar el informe', 'error'); return; }
            const data = await r.json();
            renderKpis(data);
            setMsg('');
            await paintAdelantosYNeto(qs);
        } catch {
            setMsg('Error cargando el informe', 'error');
        }
    }

    // ===== Tablas (DataTables) =====
    function refLink(row) {
        const refText = escapeHtml(row.referencia || '—');
        return row.id
            ? `<a class="row-link" href="/tickets/crear?id=${row.id}" title="Abrir ticket">${refText}</a>`
            : refText;
    }
    const fmtImporte = v => (v != null ? Number(v).toFixed(2).replace('.', ',') + ' €' : '—');

    let dtCobrados = null;

    function initCobradosTable() {
        if (dtCobrados) return;

        dtCobrados = jQuery('#tickets-cobrados-table').DataTable({
            pageLength: 15,
            lengthMenu: [10, 15, 25, 50, 100],
            ordering: true,
            searching: false,
            autoWidth: false,
            language: { url: "https://cdn.datatables.net/plug-ins/1.13.8/i18n/es-ES.json", infoEmpty: "Sin registros", emptyTable: "No hay tickets cobrados en el periodo seleccionado" },
            columns: [
                { data: "fecha", render: (v) => (v || '').toString().substring(0, 10) },
                { data: null, render: (row) => refLink(row) },
                { data: "matricula", render: (v) => escapeHtml((v || '—').toUpperCase()) },
                { data: "marca", render: (v) => escapeHtml(v || '—') },
                { data: "modelo", render: (v) => escapeHtml(v || '—') },
                { data: "metodoPago", render: (v) => escapeHtml(METODO_LABEL[v] || v || '—') },
                { data: "total", className: "right", render: (v) => fmtImporte(v) }
            ],
            initComplete: function() { window.dtMoveLengthControl('tickets-cobrados-table'); }
        });
    }

    function renderCobradosRows(list) {
        initCobradosTable();
        dtCobrados.clear();
        dtCobrados.rows.add(list || []);
        dtCobrados.draw();
    }

    let dtPendientes = null;

    function initPendientesTable() {
        if (dtPendientes) return;

        dtPendientes = jQuery('#tickets-pendientes-table').DataTable({
            pageLength: 15,
            lengthMenu: [10, 15, 25, 50, 100],
            ordering: true,
            searching: false,
            autoWidth: false,
            language: { url: "https://cdn.datatables.net/plug-ins/1.13.8/i18n/es-ES.json", infoEmpty: "Sin registros", emptyTable: "No hay tickets pendientes de pago" },
            columns: [
                { data: "fecha", render: (v) => (v || '').toString().substring(0, 10) },
                { data: null, render: (row) => refLink(row) },
                { data: "cliente", render: (v) => escapeHtml(v || '—') },
                { data: "clienteTelefono", render: (v) => escapeHtml(v || '—') },
                { data: "matricula", render: (v) => escapeHtml(v || '—') },
                { data: "centro", render: (v) => escapeHtml(v || '—') },
                { data: "total", className: "right", render: (v) => fmtImporte(v) }
            ],
            initComplete: function() { window.dtMoveLengthControl('tickets-pendientes-table'); }
        });
    }

    function renderPendientesRows(list) {
        initPendientesTable();
        dtPendientes.clear();
        dtPendientes.rows.add(list || []);
        dtPendientes.draw();
    }

    // ===== Lista de tickets =====
    async function loadTicketList(pageIdx = 0) {
        const fdesde = ($('#fdesde').value || '').trim();
        const fhasta = ($('#fhasta').value || '').trim();
        if (!fdesde || !fhasta) { setMsg('Selecciona el rango de fechas', 'error'); return; }

        const qs = new URLSearchParams();
        qs.set('fdesde', fdesde);
        qs.set('fhasta', fhasta);
        qs.set('page', String(pageIdx));
        qs.set('size', String(pageSize));

        const cSel = document.getElementById('centroId');
        const centroId = cSel ? (cSel.value || '').trim() : '';
        if (centroId) qs.set('centroId', centroId);

        const metodoEl = $('#metodo');
        const metodo = metodoEl ? (metodoEl.value || '').trim() : '';
        if (metodo) qs.set('metodoPago', metodo);

        ['PAGADO', 'CERRADO'].forEach(est => qs.append('estados', est));

        try {
            const r = await af('/api/tickets/page?' + qs.toString(), { headers: { 'Accept': 'application/json' } });
            if (!r.ok) { let t = ''; try { t = (await r.text())?.trim(); } catch { } setMsg(t || 'No se pudo cargar la lista de tickets', 'error'); return; }
            const page = await r.json();
            const rows = Array.isArray(page) ? page : (page.content || []);
            currentPage = pageIdx;
            renderCobradosRows(rows);
            setMsg('');
        } catch {
            setMsg('Error cargando la lista de tickets', 'error');
        }
    }

    function readFilters() {
        try {
            const raw = sessionStorage.getItem(STORAGE_KEY);
            return raw ? JSON.parse(raw) : {};
        } catch {
            return {};
        }
    }

    function saveFilters() {
        const data = {
            desde: ($('#fdesde')?.value || '').trim(),
            hasta: ($('#fhasta')?.value || '').trim(),
            centroId: ($('#centroId')?.value || '').trim(),
            metodo: ($('#metodo')?.value || '').trim()
        };
        sessionStorage.setItem(STORAGE_KEY, JSON.stringify(data));
    }

    function markRestore() {
        saveFilters();
        sessionStorage.setItem(RESTORE_KEY, '1');
    }

    function navigationType() {
        const nav = performance.getEntriesByType('navigation')?.[0];
        if (nav?.type) return nav.type;
        const legacy = performance.navigation?.type;
        if (legacy === 2) return 'back_forward';
        if (legacy === 1) return 'reload';
        return 'navigate';
    }

    function shouldRestoreFilters() {
        if (sessionStorage.getItem(RESTORE_KEY) !== '1') return false;
        const ref = (document.referrer || '');
        if (ref.includes('/tickets/crear') || ref.includes('/pdf_viewer.html')) return true;
        return navigationType() === 'back_forward';
    }

    function applyFilters(data) {
        if (!data) return;
        if (data.desde) $('#fdesde').value = data.desde;
        if (data.hasta) $('#fhasta').value = data.hasta;
        if ($('#centroId') && data.centroId != null) $('#centroId').value = data.centroId;
        if ($('#metodo') && data.metodo != null) $('#metodo').value = data.metodo;
    }

    // ===== Pendientes de pago (solo ADMIN) =====
    async function loadPendientes() {
        if (!IS_ADMIN) {
            if (pendientesCard) pendientesCard.style.display = 'none';
            return;
        }

        const fdesde = ($('#fdesde').value || '').trim();
        const fhasta = ($('#fhasta').value || '').trim();
        if (!fdesde || !fhasta) { return; }

        const qs = new URLSearchParams();
        qs.set('fdesde', fdesde);
        qs.set('fhasta', fhasta);
        qs.set('page', '0');
        qs.set('size', String(pageSize));

        const cSel = document.getElementById('centroId');
        const centroId = cSel ? (cSel.value || '').trim() : '';
        if (centroId) qs.set('centroId', centroId);

        qs.append('estados', 'PTE_PAGO');

        try {
            const r = await af('/api/tickets/page?' + qs.toString(), { headers: { 'Accept': 'application/json' } });
            if (!r.ok) { if (pendientesCard) pendientesCard.style.display = 'none'; return; }
            const page = await r.json();
            const rows = Array.isArray(page) ? page : (page.content || []);

            if (pendientesCard) pendientesCard.style.display = 'block';
            renderPendientesRows(rows);
        } catch {
            if (pendientesCard) pendientesCard.style.display = 'none';
        }
    }

    // ===== Init =====
    (async function init() {
        const me = await ensureAuthenticated();
        if (!me) return;
        IS_ADMIN = me?.role === 'ROLE_ADMIN';

        if (!IS_ADMIN) {
            const helpText = 'Solo puedes consultar el día actual';
            const desdeInput = document.getElementById('fdesde');
            const hastaInput = document.getElementById('fhasta');
            if (desdeInput) { desdeInput.disabled = true; desdeInput.title = helpText; }
            if (hastaInput) { hastaInput.disabled = true; hastaInput.title = helpText; }
            document.getElementById('btnMesActual')?.remove();
            document.getElementById('btnUlt7')?.remove();
            document.getElementById('btnHoy')?.remove();
        } else {
            const today = toDateStr(new Date());
            const desdeInput = document.getElementById('fdesde');
            const hastaInput = document.getElementById('fhasta');
            if (desdeInput) desdeInput.max = today;
            if (hastaInput) hastaInput.max = today;
            desdeInput?.addEventListener('change', () => {
                if (!hastaInput) return;
                hastaInput.min = desdeInput.value || '';
                if (hastaInput.value && hastaInput.value < desdeInput.value) hastaInput.value = '';
            });
        }

        await loadCentros(me);

        const canRestore = shouldRestoreFilters();
        if (canRestore) {
            applyFilters(readFilters());
            sessionStorage.removeItem(RESTORE_KEY);
        } else {
            sessionStorage.removeItem(RESTORE_KEY);
            if (navigationType() === 'reload') sessionStorage.removeItem(STORAGE_KEY);
            setRangeHoy();
        }

        // ===== Eventos de botones =====
        $('#btnAplicar')?.addEventListener('click', () => { saveFilters(); loadResumen(); loadTicketList(); loadPendientes(); });
        $('#btnMesActual')?.addEventListener('click', () => { setRangeMesActual(); saveFilters(); loadResumen(); loadTicketList(); loadPendientes(); });
        $('#btnUlt7')?.addEventListener('click', () => { setRangeUlt7(); saveFilters(); loadResumen(); loadTicketList(); loadPendientes(); });
        $('#btnHoy')?.addEventListener('click', () => { setRangeHoy(); saveFilters(); loadResumen(); loadTicketList(); loadPendientes(); });

        $('#btnImprimirInforme')?.addEventListener('click', () => {
            const hoyStr = toDateStr(new Date());
            const rawDesde = ($('#fdesde').value || '').trim();
            const rawHasta = ($('#fhasta').value || '').trim();
            const desde = IS_ADMIN ? rawDesde : hoyStr;
            const hasta = IS_ADMIN ? rawHasta : hoyStr;

            if (!desde || !hasta) {
                setMsg('Selecciona el rango de fechas', 'error');
                return;
            }
            const qs = new URLSearchParams();
            qs.set('desde', desde);
            qs.set('hasta', hasta);

            const centroId = document.getElementById('centroId')?.value?.trim();
            if (centroId) qs.set('centroId', centroId);

            const metodo = document.getElementById('metodo')?.value?.trim();
            if (metodo) qs.set('metodoPago', metodo);

            ['PAGADO', 'CERRADO'].forEach(est => qs.append('estados', est));

            markRestore();
            const url = '/api/informes/cierre/print?' + qs.toString();
            const viewerUrl = '/pdf_viewer.html?src=' + encodeURIComponent(url);
            window.location.href = viewerUrl;
        });

        document.addEventListener('click', (ev) => {
            const link = ev.target.closest('a.row-link');
            if (link) markRestore();
        });

        ['fdesde', 'fhasta', 'centroId', 'metodo'].forEach(id => {
            const el = document.getElementById(id);
            if (el) el.addEventListener('change', saveFilters);
        });

        await Promise.all([loadResumen(), loadTicketList(), loadPendientes()]);
        document.querySelector('main').style.opacity = '1';
    })();
})();
