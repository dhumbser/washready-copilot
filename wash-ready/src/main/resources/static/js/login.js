(function () {
    const $ = (q) => document.querySelector(q);
    const msgEl = $('#msg');
    const loginForm = $('#login-form');

    function setMsg(text, type) {
        msgEl.textContent = text;
        msgEl.className = 'alert';
        if (type) msgEl.classList.add(type);
        msgEl.style.display = text ? 'block' : 'none';
        if (text) { const r = msgEl.getBoundingClientRect(); if (r.top < 0 || r.bottom > window.innerHeight) msgEl.scrollIntoView({ behavior: 'smooth', block: 'nearest' }); }
    }

    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        setMsg('Validando…');
        const formData = new FormData(loginForm);
        try {
            const res = await fetch(loginForm.action, {
                method: 'POST',
                headers: { 'Accept': 'application/json' },
                body: new URLSearchParams(formData)
            });
            const raw = await res.text();
            let data = null;
            try { data = raw ? JSON.parse(raw) : null; } catch (_) {}

            if (res.ok) {
                if (!data || !data.access_token || !data.refresh_token) {
                    setMsg('Respuesta inesperada del servidor', 'error');
                    return;
                }
                localStorage.setItem('access_token', data.access_token);
                localStorage.setItem('refresh_token', data.refresh_token);
                window.location.href = '/';
            } else {
                const fromJson = (data && data.message) ? String(data.message) : '';
                const fromText = (raw && !raw.trim().startsWith('<')) ? raw.trim() : '';
                const backendMsg = fromJson || fromText;

                if (res.status === 401) {
                    setMsg(backendMsg || 'Credenciales inválidas', 'error');
                } else if (res.status === 403) {
                    setMsg(backendMsg || 'Acceso denegado', 'error');
                } else {
                    setMsg(backendMsg || 'Error inesperado', 'error');
                }
            }
        } catch (err) {
            setMsg('No se pudo conectar con el servidor', 'error');
        }
    });
})();