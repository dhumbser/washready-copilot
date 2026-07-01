(function () {
  const af = window.authFetch;
  const escapeHtml = window.escapeHtml;

  const $ = (q) => document.querySelector(q);
  const editorContainer = $('#editorContainer'),
    form = $('#form'),
    toggle = $('#toggleForm'),
    cancel = $('#cancel'),
    tbody = $('#empresas-tbody');
  const id = $('#id'), nombre = $('#nombre'), direccion = $('#direccion'),
    municipio = $('#municipio'), provincia = $('#provincia'), pais = $('#pais'),
    codigoPostal = $('#codigoPostal'), correo = $('#correo'), telefono = $('#telefono'), cif = $('#cif');

  function scrollToForm() {
    editorContainer.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
  function payload() {
    return {
      nombre: nombre.value.trim(),
      direccion: direccion.value.trim() || null,
      municipio: municipio.value.trim() || null,
      provincia: provincia.value.trim() || null,
      pais: pais.value.trim() || null,
      codigoPostal: codigoPostal.value.trim() || null,
      correo: correo.value.trim() || null,
      telefono: telefono.value.trim() || null,
      cif: cif.value.trim()
    };
  }

  async function load() {
    const res = await af('/api/empresas', { headers: { 'Accept': 'application/json' } });
    if (!res.ok) { setMsg('No se pudo cargar empresas (' + res.status + ')', 'error'); return; }
    renderRows(await res.json());
  }

  let dt = null;

  function initDataTable() {
    if (dt) return;

    dt = jQuery('#empresas-table').DataTable({
      pageLength: 15,
      lengthMenu: [10, 15, 25, 50, 100],
      ordering: true,
      searching: false,
      autoWidth: false,
      language: {
        url: "https://cdn.datatables.net/plug-ins/1.13.8/i18n/es-ES.json",
        infoEmpty: "Sin registros",
        emptyTable: "No hay empresas registradas"
      },
      columns: [
        { data: "nombre", render: (v) => `<strong>${escapeHtml(v || '')}</strong>` },
        { data: "cif", render: (v) => v ? escapeHtml(v) : '<span class="muted">-</span>' },
        { data: "direccion", render: (v) => v ? escapeHtml(v) : '<span class="muted">-</span>' },
        { data: "municipio", render: (v) => v ? escapeHtml(v) : '<span class="muted">-</span>' },
        { data: "provincia", render: (v) => v ? escapeHtml(v) : '<span class="muted">-</span>' },
        { data: "pais", render: (v) => v ? escapeHtml(v) : '<span class="muted">-</span>' },
        { data: "codigoPostal", render: (v) => v ? escapeHtml(v) : '<span class="muted">-</span>' },
        { data: "telefono", render: (v) => v ? escapeHtml(v) : '<span class="muted">-</span>' },
        { data: "correo", render: (v) => v ? escapeHtml(v) : '<span class="muted">-</span>' },
        {
          data: null,
          orderable: false,
          className: "col-actions",
          render: (row) => `<button class="secondary edit" data-id="${row.id}">Editar</button>`
        }
      ],
      initComplete: function() { window.dtMoveLengthControl('empresas-table'); }
    });
  }

  function renderRows(list) {
    initDataTable();
    dt.clear();
    dt.rows.add(list || []);
    dt.draw();
  }

  async function init() {
    toggle.addEventListener('click', () => {
      form.reset(); id.value = '';
      editorContainer.style.display = 'block';
      toggle.style.display = 'none';
      setMsg('');
      tbody.querySelectorAll('.selected-row').forEach(tr => tr.classList.remove('selected-row'));
    });

    cancel.addEventListener('click', () => {
      form.reset(); id.value = '';
      editorContainer.style.display = 'none';
      toggle.style.display = 'block';
      setMsg('');
      tbody.querySelectorAll('.selected-row').forEach(tr => tr.classList.remove('selected-row'));
    });

    form.addEventListener('submit', async ev => {
      ev.preventDefault();
      const body = JSON.stringify(payload());
      const edit = !!id.value;
      const url = edit ? `/api/empresas/${id.value}` : '/api/empresas';
      const method = edit ? 'PUT' : 'POST';
      const res = await af(url, { method, headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' }, body });
      if (!res.ok) { setMsg(await res.text() || 'Error guardando', 'error'); return; }
      setMsg(edit ? 'Empresa actualizada' : 'Empresa creada', 'success');
      form.reset(); id.value = '';
      editorContainer.style.display = 'none';
      toggle.style.display = 'block';
      load();
    });

    tbody.addEventListener('click', async ev => {
      const t = ev.target; if (!t.closest) return;

      if (t.classList.contains('edit')) {
        const tr = t.closest('tr');
        if (tr) {
          tbody.querySelectorAll('.selected-row').forEach(r => r.classList.remove('selected-row'));
          tr.classList.add('selected-row');
        }

        const res = await af(`/api/empresas/${t.dataset.id}`, { headers: { 'Accept': 'application/json' } });
        if (!res.ok) { setMsg('No se pudo cargar empresa', 'error'); return; }
        const e = await res.json();
        id.value = e.id;
        nombre.value = e.nombre || '';
        direccion.value = e.direccion || '';
        municipio.value = e.municipio || '';
        provincia.value = e.provincia || '';
        pais.value = e.pais || '';
        codigoPostal.value = e.codigoPostal || '';
        correo.value = e.correo || '';
        telefono.value = e.telefono || '';
        cif.value = e.cif || '';
        editorContainer.style.display = 'block'; toggle.style.display = 'none'; setMsg('');
        scrollToForm();
      }
    });

    await load();
  }

  init();
})();
