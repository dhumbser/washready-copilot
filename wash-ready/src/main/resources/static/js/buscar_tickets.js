const $ = (q) => document.querySelector(q);

let me = null, roleAdmin = false;
async function whoAmI() {
    try {
    const r = await authFetch('/auth/me', { headers: { 'Accept': 'application/json' } });
    if (r.status === 401) { window.location = '/login.html'; return; }
    me = await r.json();
    $('#username').textContent = me.usuario || '—';
    $('#centro').textContent = me.centroTrabajo || '—';
    roleAdmin = me.role === 'ROLE_ADMIN';
    } catch { }
}

const btnAbiertos = $('#btnAbiertos');
const btnCerrados = $('#btnCerrados');
const btnAnulados = $('#btnAnulados');

const tbody = $('#tickets-tbody');

const filters = {
    matricula: $('#fMatricula'),
    referencia: $('#fReferencia'),
    marca: $('#fMarca'),
    modelo: $('#fModelo'),
    color: $('#fColor'),
    cliente: $('#fCliente'),
    telefono: $('#fTelefono'),
    fdesde: $('#fDesde'),
    fhasta: $('#fHasta'),
    estado: $('#fEstado'),
    centroId: $('#fCentro')
};

const state = { estados: null };
const PAGE_SIZE = 10000;

function buildQuery() {
    const p = new URLSearchParams();
    p.set('page', 0); p.set('size', PAGE_SIZE);
    for (const [k, el] of Object.entries(filters)) {
    const v = (el.value || '').trim();
    if (v) {
        if (k === 'estado' && state.estados) continue;
        p.set(k, v);
    }
    }
    if (state.estados && state.estados.length) {
    p.set('estados', state.estados.join(','));
    }
    return '?' + p.toString();
}

function fmt(n) { return (Number(n || 0)).toFixed(2).replace('.', ',') + ' €'; }
function fmtFecha(iso) {
    if (!iso) return '';
    const d = new Date(iso); const pad = n => String(n).padStart(2, '0');
    return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function pad2(n) { return String(n).padStart(2, '0'); }
function todayStr() {
    const d = new Date();
    return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}


function estadoBadge(e) {
    if (!e) return '';
    let s = String(e).trim().toUpperCase();
    // Eliminar acentos
    s = s.normalize('NFD').replace(/[\u0300-\u036f]/g, '');
    // Compactar (quitar espacios, puntos, guiones)
    const compact = s.replace(/[\s._-]/g, '');

    let code = 'NUEVO';
    if (compact === 'PTEDEPAGO' || compact === 'PENDIENTEPAGO' || compact === 'PTEPAGO') code = 'PTE_PAGO';
    else if (compact === 'PAGADO' || compact === 'PAID') code = 'PAGADO';
    else if (compact === 'CERRADO' || compact === 'CLOSED') code = 'CERRADO';
    else if (compact === 'ANULADO' || compact === 'CANCELADO') code = 'ANULADO';
    else code = s; // Fallback al valor original si no lo reconocemos

    const map = {
    PTE_PAGO: { c: 'pte', t: 'Pte. de pago' },
    PAGADO: { c: 'pago', t: 'Pagado' },
    CERRADO: { c: 'cerr', t: 'Cerrado' },
    ANULADO: { c: 'anu', t: 'Anulado' },
    NUEVO: { c: '', t: 'Nuevo' }
    };

    // Intentar buscar por código normalizado, si no, usar el valor original
    const m = map[code] || { c: '', t: e };
    return `<span class="state ${m.c}">${m.t}</span>`;
}

function clienteDecorado(cli, noDeseado) {
    return noDeseado ? `<span class="nd">${cli}</span>` : cli;
}

let dt = null;

function initDataTable() {
    if (dt) return;

    dt = jQuery('#tickets-table').DataTable({
    pageLength: 15,
    lengthMenu: [10, 15, 25, 50, 100],
    ordering: true,
    searching: false,
    autoWidth: false,
    dom: '<"table-wrapper"t>lip',
    language: {
        url: "https://cdn.datatables.net/plug-ins/1.13.8/i18n/es-ES.json",
        infoEmpty: "Sin registros",
        emptyTable: "No se encontraron tickets con los filtros aplicados"
    },
    columns: [
        { data: "fecha", className: "nowrap", render: (v) => fmtFecha(v) },
        { data: null, className: "nowrap", render: (r) => `<a class="row-link" href="/tickets/crear?id=${r.id}" title="Abrir ticket">${r.referencia || ''}</a>` },
        { data: "estado", className: "nowrap", render: (v) => estadoBadge(v) },
        { data: "total", className: "nowrap col-total", render: (v) => fmt(v) },
        { data: "centro", className: "ellipsis", render: (v) => (v || '') },
        { data: "matricula", className: "nowrap", render: (v) => (v || '') },
        { data: "marca", className: "ellipsis", render: (v) => (v || '') },
        { data: "modelo", className: "ellipsis", render: (v) => (v || '') },
        { data: "color", className: "ellipsis", render: (v) => (v || '') },
        { data: null, className: "ellipsis", render: (r) => clienteDecorado(r.cliente, !!r.clienteNoDeseado) },
        { data: "clienteTelefono", className: "ellipsis", render: (v) => (v || '') }
    ],
    initComplete: function() { window.dtMoveLengthControl('tickets-table'); }
    });
}

function renderRows(list) {
    initDataTable();
    dt.clear();
    dt.rows.add(list || []);
    dt.draw();
}

async function load() {
    const r = await authFetch('/api/tickets/page' + buildQuery(), { headers: { 'Accept': 'application/json' } });
    if (!r.ok) { setMsg('No se pudo cargar la tabla (' + r.status + ')', 'error'); return; }
    const data = await r.json();
    renderRows(data.content || []);
}

// Filtros
$('#btnBuscar').addEventListener('click', (e) => {
    e.preventDefault();

    const d = filters.fdesde.value;
    const h = filters.fhasta.value;

    // Validación: Obligatorio rango de fechas para buscar
    if (!d || !h) {
    setMsg('Debes indicar un rango de fechas', 'error');
    return;
    }

    // Validar coherencia (aunque el input type=date ayuda, no está de más)
    if (d > h) {
    setMsg('La fecha "Desde" no puede ser posterior a "Hasta"', 'error');
    return;
    }

    // Limpiamos mensajes previos si valida bien
    setMsg('');

    // Al buscar, forzamos modo de filtro manual (sin grupo de estados del chip)
    state.estados = null;

    // Actualizamos visualmente qué chip "encaja" con el estado seleccionado
    [btnAbiertos, btnCerrados, btnAnulados].forEach(b => b.classList.remove('active'));
    const v = filters.estado.value;
    if (v === 'PTE_PAGO' || v === 'PAGADO') btnAbiertos.classList.add('active');
    else if (v === 'CERRADO') btnCerrados.classList.add('active');
    else if (v === 'ANULADO') btnAnulados.classList.add('active');

    load();
});
$('#btnLimpiar').addEventListener('click', (e) => {
    e.preventDefault();
    Object.values(filters).forEach(el => el.value = '');
    state.estados = null;
    [btnAbiertos, btnCerrados, btnAnulados].forEach(b => b.classList.remove('active'));
    load();
});

// Chips de estado
function setChip(chip, estados) {
    [btnAbiertos, btnCerrados, btnAnulados].forEach(b => b.classList.remove('active'));
    chip.classList.add('active');
    state.estados = estados;
    filters.estado.value = '';
    load();
}
btnAbiertos.addEventListener('click', () => setChip(btnAbiertos, ['PTE_PAGO', 'PAGADO']));
btnCerrados.addEventListener('click', () => setChip(btnCerrados, ['CERRADO']));
btnAnulados.addEventListener('click', () => setChip(btnAnulados, ['ANULADO']));

// --- Persistencia de Filtros (Navegación) ---
function saveState() {
    const s = {
    estados: state.estados,
    vals: {}
    };
    for (const key in filters) {
    if (filters[key]) s.vals[key] = filters[key].value;
    }
    sessionStorage.setItem('ticketsState', JSON.stringify(s));
    sessionStorage.setItem('fromTicketsList', '1');
}

function restoreState() {
    // 1. Si es refresco (F5), limpiar (no restaurar)
    try {
    const nav = performance.getEntriesByType("navigation")[0];
    if (nav && nav.type === 'reload') return false;
    } catch (e) { }

    // 2. Solo restauramos si venimos de la ficha...
    if (!document.referrer || !document.referrer.includes('/tickets/crear')) {
    // ...pero si NO venimos de la ficha, asegurarnos de LIMPIAR el estado para el futuro
    // (Por ejemplo, vienes de Home -> Tickets directamente)
    sessionStorage.removeItem('ticketsState');
    sessionStorage.removeItem('fromTicketsList');
    return false;
    }

    // 3. Y ADEMÁS, solo si la ficha fue abierta DESDE el listado previamente
    if (!sessionStorage.getItem('fromTicketsList')) {
    // Si no está el flag, significa que entraste a la ficha desde Home (Nuevo ticket)
    // y luego pulsaste Volver. En ese caso NO queremos restaurar filtros viejos.
    // Limpiamos todo para que cargue por defecto.
    sessionStorage.removeItem('ticketsState');
    return false;
    }
    // Consumimos el flag (opcional, pero buena práctica si solo queremos 1 nivel de back)
    // sessionStorage.removeItem('fromTicketsList'); // Lo dejamos por si el usuario va adelante/atrás varias veces

    const raw = sessionStorage.getItem('ticketsState');
    if (!raw) return false;

    try {
    const s = JSON.parse(raw);
    state.estados = s.estados;

    // Restaurar inputs
    for (const key in filters) {
        if (filters[key] && s.vals[key] !== undefined) {
        filters[key].value = s.vals[key];
        }
    }

    // Restaurar UI Chips
    [btnAbiertos, btnCerrados, btnAnulados].forEach(b => b.classList.remove('active'));

    // Lógica de visualización de chips (similar a btnBuscar)
    // 1. Si hay estados definidos (Modo Chip)
    if (state.estados && state.estados.length > 0) {
        const j = state.estados.join(',');
        if (j.includes('PTE_PAGO') || j.includes('PAGADO')) btnAbiertos.classList.add('active');
        else if (j.includes('CERRADO')) btnCerrados.classList.add('active');
        else if (j.includes('ANULADO')) btnAnulados.classList.add('active');
    }
    // 2. Si es filtro manual, tratamos de iluminar si coincide
    else {
        const v = filters.estado.value;
        if (v === 'PTE_PAGO' || v === 'PAGADO') btnAbiertos.classList.add('active');
        else if (v === 'CERRADO') btnCerrados.classList.add('active');
        else if (v === 'ANULADO') btnAnulados.classList.add('active');
    }

    load();
    return true;
    } catch (e) {
    console.error('Error restaurando estado:', e);
    return false;
    }
}

// Guardar estado al hacer click en un ticket (row-link)
document.addEventListener('click', e => {
    if (e.target.closest('a.row-link')) {
    saveState();
    }
});

(async function init() {
    await whoAmI();
    await loadCentros();
    document.querySelector('main').style.opacity = '1';

    // Restricciones de fecha
    const hoy = todayStr();
    if (filters.fdesde) filters.fdesde.max = hoy;
    if (filters.fhasta) filters.fhasta.max = hoy;
    filters.fdesde?.addEventListener('change', () => {
        if (!filters.fhasta) return;
        filters.fhasta.min = filters.fdesde.value || '';
        if (filters.fhasta.value && filters.fhasta.value < filters.fdesde.value) filters.fhasta.value = '';
    });

    // Intentar restaurar filtros si venimos de ficha
    if (restoreState()) {
    return; // Si se restauró, ya se llama a load() dentro
    }

    // Si no, valores por defecto
    if (filters.fdesde) filters.fdesde.value = hoy;
    if (filters.fhasta) filters.fhasta.value = hoy;

    // Filtro por defecto: Abiertos
    setChip(btnAbiertos, ['PTE_PAGO', 'PAGADO']);
})();

async function loadCentros() {
    const sel = $('#fCentro');
    if (!sel) return;

    const r = await authFetch('/api/centros', { headers: { 'Accept': 'application/json' } });
    if (!r.ok) return;

    let centros = await r.json(); // [{id, nombre, ...}, ...]

    // Si NO es admin, filtra por su centroId
    if (!roleAdmin) {
    const myCentroId = Number(me?.centroId);
    centros = centros.filter(c => Number(c.id) === myCentroId);
    }

    sel.innerHTML = `<option value="">(Todos)</option>`;

    centros.forEach(c => {
    const opt = document.createElement('option');
    opt.value = c.id;          // enviamos ID
    opt.textContent = c.nombre; // mostramos nombre
    sel.appendChild(opt);
    });

    // Si NO admin: preselecciona y bloquea
    if (!roleAdmin && centros.length === 1) {
    sel.value = String(centros[0].id);
    sel.disabled = true;
    } else {
    sel.disabled = false;
    }
}