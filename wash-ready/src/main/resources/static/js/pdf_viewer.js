const $ = s => document.querySelector(s);

let objectUrl = null;

function getParam(name){
    return new URLSearchParams(location.search).get(name);
}

function buildSrcFromParams(){
    const explicit = getParam('src');
    if (explicit) return explicit;

    const kind = (getParam('kind')||'').trim().toLowerCase();
    const id   = (getParam('id')||'').trim();
    if (!kind || !id) return null;

    if (kind !== 'tickets' && kind !== 'adelantos') return null;
    return `/api/${kind}/${encodeURIComponent(id)}/print`;
}

async function loadPdf(){
    const url = buildSrcFromParams();
    if (!url){ setMsg('Faltan parámetros (?kind=tickets|adelantos&id=...) o ?src=...', 'error'); return; }

    setMsg('Generando PDF…');
    try{
    const res = await authFetch(url, { headers: { 'Accept': 'application/pdf' }});
    if (!res.ok){
        let t=''; try{ t=(await res.text())?.trim(); }catch{}
        setMsg(t || 'No se pudo generar el PDF', 'error');
        return;
    }
    const blob = await res.blob();
    if (objectUrl) URL.revokeObjectURL(objectUrl);
    objectUrl = URL.createObjectURL(blob);
    $('#pdfFrame').src = objectUrl;

    const a = $('#openNew');
    a.href = objectUrl;
    a.style.display = '';

    setMsg('');
    }catch(e){
    setMsg('Error cargando el PDF', 'error');
    }
}

window.addEventListener('beforeunload', () => {
    if (objectUrl) URL.revokeObjectURL(objectUrl);
});

(async function init(){
    // fuerza sesión 
    await authFetch('/auth/me', { headers:{'Accept':'application/json'} });
    await loadPdf();
})();