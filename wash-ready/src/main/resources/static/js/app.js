(function () {
    window.escapeHtml = (s) => String(s ?? '').replace(/[&<>"']/g, ch => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[ch]));

    window.setMsg = function(t, type = 'success') {
        const el = document.getElementById('msg');
        if (!el) return;
        el.textContent = t || '';
        el.className = 'alert ' + (t ? type : '');
        el.style.display = t ? 'block' : 'none';
        if (t) { const r = el.getBoundingClientRect(); if (r.top < 0 || r.bottom > window.innerHeight) el.scrollIntoView({ behavior: 'smooth', block: 'nearest' }); }
    };

    window.dtMoveLengthControl = function(tableId) {
        const slotId = tableId.replace(/-table$/, '') + '-length';
        const wrapper = document.querySelector('#' + tableId + '_wrapper');
        const lengthControl = wrapper?.querySelector('.dataTables_length');
        const lengthSlot = document.querySelector('#' + slotId);
        if (lengthControl && lengthSlot && !lengthSlot.contains(lengthControl)) {
            lengthSlot.appendChild(lengthControl);
        }
    };

    const getAT = () => localStorage.getItem('access_token');
    const getRT = () => localStorage.getItem('refresh_token');
    const setAT = (t) => localStorage.setItem('access_token', t);
    const setRT = (t) => localStorage.setItem('refresh_token', t);
    const clearTokens = () => { localStorage.removeItem('access_token'); localStorage.removeItem('refresh_token'); };

    function getOrCreateDeviceId() {
        let did = localStorage.getItem('device_id');
        if (!did) {
            did = (crypto.randomUUID ? crypto.randomUUID()
                : Date.now().toString(36) + Math.random().toString(36).slice(2));
            localStorage.setItem('device_id', did);
        }
        return did;
    }

    async function refreshIfNeeded() {
        const rt = getRT();
        if (!rt) return false;
        const res = await fetch('/auth/refresh', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'Accept': 'application/json' },
            body: new URLSearchParams({ refresh_token: rt })
        });
        if (!res.ok) return false;
        const data = await res.json();
        if (data && data.access_token) setAT(data.access_token);
        if (data && data.refresh_token) setRT(data.refresh_token);
        return true;
    }

    window.authFetch = async function (url, options = {}) {
        const opts = { ...options, headers: { ...(options.headers || {}) } };
        const at = getAT();
        if (at) opts.headers['Authorization'] = `Bearer ${at}`;
        opts.headers['X-Device-Id'] = getOrCreateDeviceId();
        let res = await fetch(url, opts);

        // Global 403 Handler: Invalidate session immediately
        if (res.status === 403) {
            clearTokens();
            window.location.href = '/login.html';
            return res; // Stop processing (though page will likely unload)
        }

        if (res.status === 401 && getRT()) {
            const ok = await refreshIfNeeded();
            if (ok) {
                const at2 = getAT();
                if (at2) opts.headers['Authorization'] = `Bearer ${at2}`;
                res = await fetch(url, opts);

                // Check again after retry
                if (res.status === 403) {
                    clearTokens();
                    window.location.href = '/login.html';
                    return res;
                }
            }
        }
        return res;
    };

    // Mostrar/ocultar elementos solo para admin (acepta .admin-only y .only-admin)
    function applyAdminVisibility(role) {
        const adminEls = document.querySelectorAll('.admin-only, .only-admin');
        if (role === 'ROLE_ADMIN') {
            adminEls.forEach(el => { el.style.display = ''; });
        } else {
            adminEls.forEach(el => el.remove());
        }
    }

    const ME_KEY = 'washready_me';

    document.addEventListener('DOMContentLoaded', async () => {
        const usernameEl = document.getElementById('username');
        const centroEl = document.getElementById('centro');
        const logoutBtn = document.getElementById('logout');

        // Rellena la cabecera desde caché inmediatamente para evitar el parpadeo
        try {
            const cached = JSON.parse(sessionStorage.getItem(ME_KEY) || 'null');
            if (cached) {
                if (usernameEl) usernameEl.textContent = cached.usuario || '—';
                if (centroEl) centroEl.textContent = cached.centroTrabajo || '—';
                applyAdminVisibility(cached.role);
            }
        } catch { }

        // Carga sesión
        let me = null;
        try {
            const res = await authFetch('/auth/me', { headers: { 'Accept': 'application/json' } });
            if (res.status === 401) { sessionStorage.removeItem(ME_KEY); window.location.href = '/login.html'; return; }
            me = await res.json();
            sessionStorage.setItem(ME_KEY, JSON.stringify(me));
        } catch { }

        if (me) {
            if (usernameEl) usernameEl.textContent = me.usuario || '—';
            if (centroEl) centroEl.textContent = me.centroTrabajo || '—';
            applyAdminVisibility(me.role);
        }

        if (logoutBtn) {
            logoutBtn.addEventListener('click', async () => {
                try { await fetch('/auth/logout', { method: 'POST' }); } catch (e) { }
                clearTokens();
                sessionStorage.removeItem(ME_KEY);
                window.location.href = '/login.html';
            });
        }
    });
})();
