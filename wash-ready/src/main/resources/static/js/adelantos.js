(function () {
    const $ = s => document.querySelector(s);
    const af = window.authFetch;
    const escapeHtml = window.escapeHtml;

    const fmtEur = v => (Number(v || 0)).toFixed(2).replace('.', ',') + ' €';
    const fmtDate = iso => {
        if (!iso) return '—';
        const d = new Date(iso);
        const p = n => String(n).padStart(2, '0');
        return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()} ${p(d.getHours())}:${p(d.getMinutes())}`;
    };

    function nifLetter(num) {
        return 'TRWAGMYFPDXBNJZSQVHLCKE'[num % 23];
    }

    function isValidDniNie(value) {
        const raw = String(value ?? '').trim().toUpperCase();
        if (raw.length !== 9) return false;
        const dniMatch = raw.match(/^(\d{8})([A-Z])$/);
        if (dniMatch) return dniMatch[2] === nifLetter(Number(dniMatch[1]));
        const nieMatch = raw.match(/^([XYZ])(\d{7})([A-Z])$/);
        if (nieMatch) {
            const prefix = { X: '0', Y: '1', Z: '2' }[nieMatch[1]];
            return nieMatch[3] === nifLetter(Number(prefix + nieMatch[2]));
        }
        return false;
    }

    let ME = null, IS_ADMIN = false;

    // El backend no limita página: pedimos todo de una vez y DataTables pagina en cliente.
    const PAGE_SIZE = 10000;

    async function loadMe() {
        const r = await af('/auth/me', { headers: { Accept: 'application/json' } });
        if (r.status === 401) { location = '/login.html'; return; }
        ME = await r.json();
        IS_ADMIN = (ME.role === 'ROLE_ADMIN');
        $('#username').textContent = ME.usuario || '—';
        $('#centro').textContent = ME.centroTrabajo || '—';
    }

    // ========== USER ==========
    function userQuery() {
        const qs = new URLSearchParams();
        const e = $('#fEstadoUser').value;
        const f = $('#fFromUser').value;
        const t = $('#fToUser').value;
        if (e) qs.set('estado', e);
        if (f) qs.set('from', f);
        if (t) qs.set('to', t);
        qs.set('page', 0); qs.set('size', PAGE_SIZE);
        return qs.toString();
    }

    let dtMine = null;

    function initMineTable() {
        if (dtMine) return;
        dtMine = jQuery('#adelantos-mine-table').DataTable({
            pageLength: 15,
            lengthMenu: [10, 15, 25, 50, 100],
            ordering: true,
            searching: false,
            autoWidth: false,
            language: { url: 'https://cdn.datatables.net/plug-ins/1.13.8/i18n/es-ES.json', infoEmpty: 'Sin registros', emptyTable: 'No tienes adelantos registrados' },
            columns: [
                { data: 'creadoAt',   render: v => fmtDate(v) },
                { data: 'decididoAt', render: v => fmtDate(v) },
                { data: 'importe',    render: v => fmtEur(v) },
                {
                    data: null,
                    render: row => {
                        const labels = { PENDIENTE: 'Pendiente', ACEPTADO: 'Aceptado', RECHAZADO: 'Rechazado', CANCELADO: 'Cancelado' };
                        let html = `<span class="status ${escapeHtml(row.estado)}">${labels[row.estado] || escapeHtml(row.estado)}</span>`;
                        if (row.motivoRechazo) {
                            html += `<br><small style="color:var(--muted)">${escapeHtml(row.motivoRechazo)}</small>`;
                        }
                        return html;
                    }
                },
                {
                    data: null,
                    orderable: false,
                    className: 'col-actions',
                    render: row => {
                        const cancelBtn = row.estado === 'PENDIENTE'
                            ? `<button class="secondary cancel" data-id="${row.id}" style="margin-right:4px;">Cancelar</button>`
                            : '';
                        return cancelBtn + `<button class="secondary print" data-id="${row.id}">Imprimir</button>`;
                    }
                }
            ],
            initComplete: function() { window.dtMoveLengthControl('adelantos-mine-table'); }
        });
    }

    function renderMineRows(list) {
        initMineTable();
        dtMine.clear(); dtMine.rows.add(list || []); dtMine.draw();
    }

    async function loadDisponible() {
        const info = $('#disponibleInfo');
        const btn  = document.getElementById('btnNuevaSolicitud');
        if (!info) return;
        try {
            const r = await af('/api/adelantos/my/disponible', { headers: { Accept: 'application/json' } });
            if (!r.ok) return;
            const { disponible } = await r.json();
            const agotado = Number(disponible) <= 0;
            info.textContent = agotado
                ? 'Límite mensual alcanzado'
                : `Disponible este mes: ${fmtEur(disponible)}`;
            info.style.color = agotado ? '#b40000' : 'var(--muted)';
            if (btn) btn.disabled = agotado;
        } catch {}
    }

    async function loadMine() {
        const r = await af('/api/adelantos/my?' + userQuery(), { headers: { Accept: 'application/json' } });
        if (!r.ok) { setMsg('No se pudieron cargar tus solicitudes', 'error'); return; }
        const page = await r.json();
        renderMineRows(page.content || []);
    }

    // ========== ADMIN ==========
    function admQuery() {
        const qs = new URLSearchParams();
        const e = $('#fEstadoAdm').value;
        const f = $('#fFromAdm').value;
        const t = $('#fToAdm').value;
        if (e) qs.set('estado', e);
        if (f) qs.set('from', f);
        if (t) qs.set('to', t);
        qs.set('page', 0); qs.set('size', PAGE_SIZE);
        return qs.toString();
    }

    let dtAdmin = null;

    function initAdminTable() {
        if (dtAdmin) return;
        dtAdmin = jQuery('#adelantos-admin-table').DataTable({
            pageLength: 15,
            lengthMenu: [10, 15, 25, 50, 100],
            ordering: true,
            searching: false,
            autoWidth: false,
            language: { url: 'https://cdn.datatables.net/plug-ins/1.13.8/i18n/es-ES.json', infoEmpty: 'Sin registros', emptyTable: 'No hay adelantos registrados' },
            columns: [
                { data: 'creadoAt',   render: v => fmtDate(v) },
                { data: 'decididoAt', render: v => fmtDate(v) },
                {
                    data: null,
                    render: row => escapeHtml([row.operarioNombre, row.operarioApellido].filter(Boolean).join(' ') || '—')
                },
                { data: 'centroNombre', render: v => escapeHtml(v || '—') },
                { data: 'importe',      render: v => fmtEur(v) },
                {
                    data: 'estado',
                    render: v => {
                        const labels = { PENDIENTE: 'Pendiente', ACEPTADO: 'Aceptado', RECHAZADO: 'Rechazado', CANCELADO: 'Cancelado' };
                        return `<span class="status ${escapeHtml(v)}">${labels[v] || escapeHtml(v)}</span>`;
                    }
                },
                {
                    data: null,
                    orderable: false,
                    className: 'col-actions',
                    render: row => {
                        const actions = row.estado === 'PENDIENTE'
                            ? `<button class="ok" data-id="${row.id}" style="margin-right:4px;">Aceptar</button><button class="no secondary" data-id="${row.id}" style="margin-right:4px;">Rechazar</button>`
                            : '';
                        return actions + `<button class="secondary print" data-id="${row.id}">Imprimir</button>`;
                    }
                }
            ],
            initComplete: function() { window.dtMoveLengthControl('adelantos-admin-table'); }
        });
    }

    function renderAdminRows(list) {
        initAdminTable();
        dtAdmin.clear(); dtAdmin.rows.add(list || []); dtAdmin.draw();
    }

    async function loadAdmin() {
        const r = await af('/api/adelantos/admin?' + admQuery(), { headers: { Accept: 'application/json' } });
        if (!r.ok) { setMsg('No se pudieron cargar las solicitudes', 'error'); return; }
        const page = await r.json();
        renderAdminRows(page.content || []);
    }

    // ---- Modal rechazo ----
    function setRechazoMsg(t, type = 'error') {
        const el = document.getElementById('rechazoMsg');
        if (!el) return;
        el.textContent = t || '';
        el.className = 'alert ' + type;
        el.style.display = t ? 'block' : 'none';
    }

    // ---- Modal nueva solicitud ----
    function setSolicitudMsg(t, type = 'success') {
        const el = document.getElementById('solicitudMsg');
        if (!el) return;
        el.textContent = t || '';
        el.className = 'alert ' + type;
        el.style.display = t ? 'block' : 'none';
    }
    function openSolicitudModal() {
        document.getElementById('formNew')?.reset();
        setNifWarn('');
        setSolicitudMsg('');
        document.getElementById('solicitudModal')?.classList.add('open');
    }
    function closeSolicitudModal() {
        document.getElementById('solicitudModal')?.classList.remove('open');
    }

    // ---- Validación inline NIF ----
    function setNifWarn(text) {
        const w = $('#nifWarn');
        if (!w) return;
        w.textContent = text || '';
        w.style.display = text ? 'block' : 'none';
    }

    // ---- Restricciones de fecha ----
    function setupDateConstraints() {
        const today = new Date().toISOString().slice(0, 10);
        ['#fFromUser', '#fToUser', '#fFromAdm', '#fToAdm'].forEach(sel => {
            const el = $(sel); if (el) el.max = today;
        });

        $('#fFromUser')?.addEventListener('change', () => {
            const to = $('#fToUser'); if (!to) return;
            to.min = $('#fFromUser').value || '';
            if (to.value && to.value < $('#fFromUser').value) to.value = '';
        });

        $('#fFromAdm')?.addEventListener('change', () => {
            const to = $('#fToAdm'); if (!to) return;
            to.min = $('#fFromAdm').value || '';
            if (to.value && to.value < $('#fFromAdm').value) to.value = '';
        });
    }

    async function init() {
        await loadMe();
        if (!ME) return;

        setupDateConstraints();

        if (IS_ADMIN) {
            document.getElementById('adminView').style.display = 'block';
            await loadAdmin();
        } else {
            document.getElementById('userView').style.display = 'block';
            await Promise.all([loadMine(), loadDisponible()]);
        }

        // ---- Filtros user ----
        $('#btnFiltrarUser')?.addEventListener('click', async e => { e.preventDefault(); await loadMine(); });
        $('#btnLimpiarUser')?.addEventListener('click', async e => {
            e.preventDefault();
            $('#fEstadoUser').value = ''; $('#fFromUser').value = ''; $('#fToUser').value = '';
            $('#fToUser').min = '';
            await loadMine();
        });

        // ---- Filtros admin ----
        $('#btnFiltrarAdm')?.addEventListener('click', async e => { e.preventDefault(); await loadAdmin(); });
        $('#btnLimpiarAdm')?.addEventListener('click', async e => {
            e.preventDefault();
            $('#fEstadoAdm').value = ''; $('#fFromAdm').value = ''; $('#fToAdm').value = '';
            $('#fToAdm').min = '';
            await loadAdmin();
        });

        // ---- Modal rechazo (admin) ----
        const closeRechazo = () => document.getElementById('rechazoModal')?.classList.remove('open');
        document.getElementById('rechazoClose')?.addEventListener('click', closeRechazo);
        document.getElementById('rechazoCancel')?.addEventListener('click', closeRechazo);
        document.querySelector('#rechazoModal .modal-backdrop')?.addEventListener('click', closeRechazo);
        document.getElementById('rechazoMotivo')?.addEventListener('input', e => {
            const cnt = document.getElementById('rechazoMotivoCount');
            if (cnt) cnt.textContent = `${e.target.value.length} / 300`;
        });

        let isRechazando = false;
        document.getElementById('formRechazo')?.addEventListener('submit', async e => {
            e.preventDefault();
            if (isRechazando) return;
            isRechazando = true;
            const btn = document.getElementById('rechazoSubmit');
            if (btn) { btn.disabled = true; btn.textContent = 'Enviando...'; }
            try {
                const id = document.getElementById('rechazoId').value;
                const motivo = (document.getElementById('rechazoMotivo').value || '').trim();
                const r = await af('/api/adelantos/' + id + '/estado', {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
                    body: JSON.stringify({ estado: 'RECHAZADO', motivo })
                });
                if (!r.ok) { setRechazoMsg(await r.text() || 'No se pudo rechazar'); return; }
                closeRechazo();
                await loadAdmin();
            } finally {
                isRechazando = false;
                if (btn) { btn.disabled = false; btn.textContent = 'Rechazar'; }
            }
        });

        // ---- Modal nueva solicitud ----
        document.getElementById('btnNuevaSolicitud')?.addEventListener('click', openSolicitudModal);
        document.getElementById('solicitudClose')?.addEventListener('click', closeSolicitudModal);
        document.getElementById('solicitudCancel')?.addEventListener('click', closeSolicitudModal);
        document.querySelector('#solicitudModal .modal-backdrop')?.addEventListener('click', closeSolicitudModal);

        // ---- Validación NIF on-blur ----
        $('#operarioNif')?.addEventListener('blur', () => {
            const val = ($('#operarioNif').value || '').trim();
            if (val && !isValidDniNie(val)) setNifWarn('El formato del DNI/NIE no es válido');
            else setNifWarn('');
        });
        $('#operarioNif')?.addEventListener('input', () => {
            if (!($('#operarioNif').value || '').trim()) setNifWarn('');
        });

        // ---- Crear solicitud (empleado) ----
        let isSubmitting = false;
        document.getElementById('formNew')?.addEventListener('submit', async e => {
            e.preventDefault();
            if (isSubmitting) return;

            const val = parseFloat($('#importe').value);
            const nombre = ($('#operarioNombre').value || '').trim();
            const apellido = ($('#operarioApellido').value || '').trim();
            const nif = ($('#operarioNif').value || '').trim();

            if (!Number.isFinite(val) || val <= 0 || val > 300) {
                setSolicitudMsg('Importe inválido (máx. 300,00 €)', 'error'); return;
            }
            if (!isValidDniNie(nif)) {
                setNifWarn('El formato del DNI/NIE no es válido');
                $('#operarioNif').focus(); return;
            }

            isSubmitting = true;
            const submitBtn = e.target.querySelector('button[type="submit"]');
            if (submitBtn) { submitBtn.disabled = true; submitBtn.textContent = 'Enviando...'; }
            try {
                const r = await af('/api/adelantos', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
                    body: JSON.stringify({ importe: val, operarioNombre: nombre, operarioApellido: apellido, operarioNif: nif })
                });
                if (!r.ok) { setSolicitudMsg(await r.text() || 'No se pudo enviar la solicitud', 'error'); return; }
                closeSolicitudModal();
                setMsg('Solicitud de adelanto enviada. Se notificará al administrador por correo electrónico.', 'success');
                await Promise.all([loadMine(), loadDisponible()]);
            } finally {
                isSubmitting = false;
                if (submitBtn) { submitBtn.disabled = false; submitBtn.textContent = 'Solicitar'; }
            }
        });

        // ---- Decisiones admin ----
        document.getElementById('adelantos-admin-tbody')?.addEventListener('click', async e => {
            const printBtn = e.target.closest('button.print');
            if (printBtn) {
                window.location.href = `/pdf_viewer.html?kind=adelantos&id=${encodeURIComponent(printBtn.dataset.id)}`;
                return;
            }
            const ok = e.target.closest('button.ok');
            if (ok) {
                const r = await af('/api/adelantos/' + ok.dataset.id + '/estado', {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
                    body: JSON.stringify({ estado: 'ACEPTADO' })
                });
                if (!r.ok) { setMsg(await r.text() || 'No se pudo actualizar', 'error'); return; }
                await loadAdmin();
                return;
            }
            const no = e.target.closest('button.no');
            if (no) {
                document.getElementById('rechazoId').value = no.dataset.id;
                document.getElementById('rechazoMotivo').value = '';
                const rcCnt = document.getElementById('rechazoMotivoCount');
                if (rcCnt) rcCnt.textContent = '0 / 300';
                setRechazoMsg('');
                document.getElementById('rechazoModal')?.classList.add('open');
            }
        });

        // ---- Imprimir y cancelar (empleado) ----
        document.getElementById('adelantos-mine-tbody')?.addEventListener('click', async e => {
            const printBtn = e.target.closest('button.print');
            if (printBtn) {
                window.location.href = `/pdf_viewer.html?kind=adelantos&id=${encodeURIComponent(printBtn.dataset.id)}`;
                return;
            }
            const cancelBtn = e.target.closest('button.cancel');
            if (cancelBtn) {
                if (!confirm('¿Cancelar esta solicitud de adelanto?')) return;
                const r = await af('/api/adelantos/' + cancelBtn.dataset.id + '/cancelar', {
                    method: 'PUT',
                    headers: { Accept: 'application/json' }
                });
                if (!r.ok) { setMsg(await r.text() || 'No se pudo cancelar', 'error'); return; }
                setMsg('Solicitud cancelada', 'success');
                await Promise.all([loadMine(), loadDisponible()]);
            }
        });
    }

    init();
})();
