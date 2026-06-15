const API_BASE = 'http://localhost:8080';
const KEYCLOAK_TOKEN_URL = 'http://localhost:8180/realms/mp3tracker/protocol/openid-connect/token';
const KEYCLOAK_CLIENT_ID = 'mp3tracker-postman';

function getToken() { return localStorage.getItem('access_token'); }
function setToken(token) { localStorage.setItem('access_token', token); }
function clearToken() { localStorage.removeItem('access_token'); }

function getRoles(token) {
    try {
        const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
        return (payload.realm_access && payload.realm_access.roles) || [];
    } catch { return []; }
}

function isAdmin(token) { return getRoles(token).includes('ADMIN'); }

function requireAuth() {
    const token = getToken();
    if (!token) { window.location.href = 'login.html'; return null; }
    return token;
}

async function apiRequest(path, method = 'GET', body = null) {
    const token = getToken();
    const options = { method, headers: { 'Authorization': `Bearer ${token}` } };
    if (body !== null) {
        options.headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(body);
    }
    const response = await fetch(API_BASE + path, options);
    if (response.status === 401) { clearToken(); window.location.href = 'login.html'; return null; }
    return response;
}

async function login(username, password) {
    const params = new URLSearchParams({
        client_id: KEYCLOAK_CLIENT_ID, grant_type: 'password', username, password
    });
    const response = await fetch(KEYCLOAK_TOKEN_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params
    });
    if (!response.ok) {
        const err = await response.json().catch(() => ({}));
        throw new Error(err.error_description || 'Invalid credentials');
    }
    const data = await response.json();
    setToken(data.access_token);
}
