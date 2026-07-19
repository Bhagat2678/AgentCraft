import React from 'react';
import { createRoot } from 'react-dom/client';
import {
  AlertCircle, BarChart3, Bell, Bot, BookOpen, BriefcaseBusiness,
  Check, CheckCircle2, ChevronLeft, ChevronRight, ClipboardCheck,
  Filter, Home, Loader2, LogOut, MessageSquare, Plug, Search,
  Send, Settings, ShieldCheck, UserRound, UsersRound, Plus,
  Building2, TrendingUp, Users, Calendar, Download, RefreshCw,
  XCircle, Clock, Star, Zap, Eye, Edit3, Trash2, Copy, ExternalLink
} from 'lucide-react';
import './styles.css';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'https://1280bed2539a671c-106-51-192-92.serveousercontent.com';

const iconMap = {
  AlertCircle, BarChart3, Bell, Bot, BookOpen, BriefcaseBusiness,
  Check, CheckCircle2, ChevronLeft, ChevronRight, ClipboardCheck,
  Filter, Home, Loader2, LogOut, MessageSquare, Plug, Search,
  Send, Settings, ShieldCheck, UserRound, UsersRound, Plus,
  Building2, TrendingUp, Users, Calendar, Download, RefreshCw,
  XCircle, Clock, Star, Zap, Eye, Edit3, Trash2, Copy, ExternalLink
};

const Icon = ({ name, size = 18, className = '' }) => {
  const LucideIcon = iconMap[name] || CheckCircle2;
  return <LucideIcon size={size} strokeWidth={1.9} aria-hidden="true" className={className} />;
};

const roles = ['CEO', 'Manager', 'Lead', 'Employee'];

const navItems = [
  { id: 'overview',    label: 'Overview',   icon: 'Home',           roles },
  { id: 'analytics',  label: 'Analytics',  icon: 'BarChart3',      roles: ['CEO', 'Manager', 'Lead'] },
  { id: 'tasks',      label: 'Tasks',      icon: 'ClipboardCheck', roles },
  { id: 'employees',  label: 'Employees',  icon: 'UsersRound',     roles: ['CEO', 'Manager', 'Lead'] },
  { id: 'departments',label: 'Depts',      icon: 'Building2',      roles: ['CEO', 'Manager'] },
  { id: 'profile',    label: 'Profile',    icon: 'UserRound',      roles },
];

// ─── API helpers ───────────────────────────────────────────────────────────────

async function apiFetch(url, jwt, options = {}) {
  const headers = {
    ...(jwt ? { Authorization: `Bearer ${jwt}` } : {}),
    ...(options.headers || {}),
  };

  if (!headers['Content-Type'] && !(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }

  const res = await fetch(url, {
    ...options,
    headers,
  });

  const text = await res.text();
  let payload = null;
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = text;
    }
  }

  if (!res.ok) {
    const message = typeof payload === 'string'
      ? payload
      : payload?.error || `HTTP ${res.status}`;
    throw new Error(message);
  }

  return payload;
}

const endpoint = (path) => `${API_BASE_URL}${path}`;

const unwrapList = (payload) => {
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.content)) return payload.content;
  if (Array.isArray(payload?.data)) return payload.data;
  return [];
};

const formatDate = (isoString) => {
  if (!isoString) return 'No date';
  return new Date(isoString).toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
};

// ─── Root App ─────────────────────────────────────────────────────────────────

function App() {
  const [phase, setPhase]   = React.useState('start');
  const [screen, setScreen] = React.useState('overview');
  const [role, setRole]     = React.useState('CEO');
  const [toast, setToast]   = React.useState({ msg: '', type: 'success' });

  // Auth
  const [jwt, setJwt]                     = React.useState(sessionStorage.getItem('jwt_token') || '');
  const [user, setUser]                   = React.useState(null);
  const [business, setBusiness]           = React.useState(null);
  const [loading, setLoading]             = React.useState(false);

  // Data
  const [tasks, setTasks]                       = React.useState([]);
  const [employees, setEmployees]               = React.useState([]);
  const [departments, setDepartments]           = React.useState([]);
  const [availableRoles, setAvailableRoles]     = React.useState([]);
  const [analytics, setAnalytics]               = React.useState(null);
  const [telegramStatus, setTelegramStatus]     = React.useState(null);
  const [dateRange, setDateRange]               = React.useState('monthly');

  // Login form
  const [loginMethod, setLoginMethod]           = React.useState('portal');
  const [emailInput, setEmailInput]             = React.useState('');
  const [businessNameInput, setBusinessNameInput] = React.useState('');
  const [passwordInput, setPasswordInput]       = React.useState('');
  const [phoneInput, setPhoneInput]             = React.useState('');
  const [inviteTokenInput, setInviteTokenInput] = React.useState('');

  const showToast = (msg, type = 'success') => setToast({ msg, type });

  React.useEffect(() => {
    if (!toast.msg) return;
    const t = setTimeout(() => setToast({ msg: '', type: 'success' }), 4500);
    return () => clearTimeout(t);
  }, [toast.msg]);

  // Telegram WebApp auto-auth
  React.useEffect(() => {
    if (window.Telegram?.WebApp) {
      const webapp = window.Telegram.WebApp;
      webapp.ready(); webapp.expand();
      const initData = webapp.initData;
      if (initData) {
        setLoading(true);
        fetch(endpoint('/api/v1/auth/telegram'), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ initData })
        })
        .then(r => r.ok ? r.json() : Promise.reject('Telegram auth failed'))
        .then(d => { setJwt(d.accessToken); sessionStorage.setItem('jwt_token', d.accessToken); })
        .catch(e => showToast(String(e), 'error'))
        .finally(() => setLoading(false));
      }
    }
  }, []);

  // Fetch profile after JWT set
  React.useEffect(() => {
    if (!jwt) return;
    setLoading(true);
    apiFetch(endpoint('/api/v1/users/me'), jwt)
      .then(profile => {
        const userRole = profile.roleNames?.[0] || profile.roles?.[0] || 'Employee';
        setUser({
          id: profile.id,
          displayName: profile.displayName,
          role: userRole,
          businessId: profile.businessId,
          primaryPhone: profile.primaryPhone || profile.phone,
          email: profile.email,
          username: profile.username,
          status: profile.status,
        });
        setBusiness(profile.business || null);
        setRole(userRole);
        setPhase('dashboard');
        loadAll(jwt);
      })
      .catch(err => {
        showToast('Session expired: ' + err.message, 'error');
        logout();
      })
      .finally(() => setLoading(false));
  }, [jwt]);

  const loadAll = (token = jwt) => {
    if (!token) return;
    Promise.all([
      apiFetch(endpoint('/api/v1/users/me'), token),
      apiFetch(endpoint('/api/v1/tasks'), token),
      apiFetch(endpoint('/api/v1/users'), token),
      apiFetch(endpoint('/api/v1/departments'), token),
      apiFetch(endpoint('/api/v1/roles'), token),
      apiFetch(endpoint(`/api/v1/analytics?dateRange=${dateRange}`), token),
      apiFetch(endpoint('/api/v1/telegram/status'), token),
    ])
      .then(([profile, taskData, userData, deptData, roleData, analyticsData, tgStatus]) => {
        setBusiness(profile.business || business);
        setTasks(unwrapList(taskData));
        setEmployees(unwrapList(userData));
        setDepartments(unwrapList(deptData));
        setAvailableRoles(unwrapList(roleData));
        setAnalytics(analyticsData);
        setTelegramStatus(tgStatus);
      })
      .catch(err => showToast(err.message || 'Failed to refresh portal data', 'error'));
  };

  React.useEffect(() => {
    if (!jwt || phase !== 'dashboard') return;
    const loaders = {
      overview: () => Promise.all([
        apiFetch(endpoint('/api/v1/analytics/summary'), jwt),
        apiFetch(endpoint('/api/v1/tasks/recent'), jwt),
        apiFetch(endpoint('/api/v1/users/count'), jwt),
        apiFetch(endpoint('/api/v1/departments'), jwt),
      ]).then(([summary, recentTasks, employeeCount, deptData]) => {
        setAnalytics(summary);
        setTasks(unwrapList(recentTasks));
        setDepartments(unwrapList(deptData));
        if (employeeCount?.total != null) {
          setEmployees(prev => prev.length ? prev : Array.from({ length: employeeCount.total }, (_, i) => ({ id: `count-${i}` })));
        }
      }),
      analytics: () => apiFetch(endpoint(`/api/v1/analytics?dateRange=${dateRange}`), jwt).then(setAnalytics),
      tasks: () => apiFetch(endpoint('/api/v1/tasks'), jwt).then(d => setTasks(unwrapList(d))),
      employees: () => Promise.all([
        apiFetch(endpoint('/api/v1/users'), jwt),
        apiFetch(endpoint('/api/v1/roles'), jwt),
        apiFetch(endpoint('/api/v1/departments'), jwt),
      ]).then(([userData, roleData, deptData]) => {
        setEmployees(unwrapList(userData));
        setAvailableRoles(unwrapList(roleData));
        setDepartments(unwrapList(deptData));
      }),
      departments: () => Promise.all([
        apiFetch(endpoint('/api/v1/departments'), jwt),
        apiFetch(endpoint('/api/v1/roles'), jwt),
      ]).then(([deptData, roleData]) => {
        setDepartments(unwrapList(deptData));
        setAvailableRoles(unwrapList(roleData));
      }),
      profile: () => Promise.all([
        apiFetch(endpoint('/api/v1/users/me'), jwt),
        apiFetch(endpoint('/api/v1/telegram/status'), jwt),
      ]).then(([profile, tgStatus]) => {
        setUser(prev => ({ ...prev, ...profile, role: profile.roleNames?.[0] || prev?.role || 'Employee' }));
        setBusiness(profile.business || business);
        setTelegramStatus(tgStatus);
      }),
    };
    loaders[screen]?.().catch(err => showToast(err.message || 'Failed to load tab data', 'error'));
  }, [screen, jwt, phase, dateRange]);

  const refresh = () => loadAll(jwt);

  const handlePortalLogin = (e) => {
    e.preventDefault();
    if (!emailInput.trim() || !businessNameInput.trim() || !passwordInput.trim()) {
      showToast('All fields are required.', 'error'); return;
    }
    setLoading(true);
    fetch(endpoint('/api/v1/auth/login'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: emailInput.trim(), businessName: businessNameInput.trim(), password: passwordInput.trim() })
    })
    .then(r => r.ok ? r.json() : r.text().then(t => Promise.reject(t || 'Invalid credentials')))
    .then(d => { setJwt(d.accessToken); sessionStorage.setItem('jwt_token', d.accessToken); })
    .catch(err => showToast(String(err), 'error'))
    .finally(() => setLoading(false));
  };

  const handleTokenLogin = (e) => {
    e.preventDefault();
    const body = phoneInput.trim() ? { phoneNumber: phoneInput.trim() } : { token: inviteTokenInput.trim() };
    if (!body.phoneNumber && !body.token) { showToast('Enter phone number or invite token.', 'error'); return; }
    setLoading(true);
    fetch(endpoint('/api/v1/auth/token'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    })
    .then(r => r.ok ? r.json() : Promise.reject('Invalid credentials'))
    .then(d => { setJwt(d.accessToken); sessionStorage.setItem('jwt_token', d.accessToken); })
    .catch(err => showToast(String(err), 'error'))
    .finally(() => setLoading(false));
  };

  const handleTaskAction = async (taskId, action, extra = {}) => {
    if (!jwt || !user) return;
    setLoading(true);
    try {
      let url, method, body;
      if (action === 'APPROVE' || action === 'REJECT') {
        url = endpoint(`/api/v1/tasks/${taskId}/approve`);
        method = 'POST';
        body = { approved: action === 'APPROVE', reason: extra.reason || '', assignmentId: extra.assignmentId };
      } else {
        url = endpoint(`/api/v1/tasks/${taskId}/status`);
        method = 'PUT';
        body = { status: action, reason: extra.reason || 'Updated via portal' };
      }
      const updated = await apiFetch(url, jwt, { method, body: JSON.stringify(body) });
      setTasks(prev => prev.map(t => t.id === taskId ? { ...t, ...updated } : t));
      showToast(`Task ${action.toLowerCase()}d successfully.`);
    } catch (err) {
      showToast(err.message, 'error');
    } finally { setLoading(false); }
  };

  const handleCreateTask = async (taskData) => {
    if (!jwt || !user) return;
    setLoading(true);
    try {
      const created = await apiFetch(endpoint('/api/v1/tasks'), jwt, {
        method: 'POST', body: JSON.stringify(taskData)
      });
      setTasks(prev => [created, ...prev]);
      showToast('Task created successfully!');
    } catch (err) { showToast(err.message, 'error'); }
    finally { setLoading(false); }
  };

  const handleInviteUser = async (inviteData) => {
    if (!jwt || !user) return;
    setLoading(true);
    try {
      const invited = await apiFetch(endpoint('/api/v1/users/invite'), jwt, {
        method: 'POST', body: JSON.stringify(inviteData)
      });
      setEmployees(prev => [invited, ...prev]);
      showToast('Invite sent!');
    } catch (err) { showToast(err.message, 'error'); }
    finally { setLoading(false); }
  };

  const handleUserStatusChange = async (userId, newStatus) => {
    if (!jwt || !user) return;
    setLoading(true);
    try {
      const updated = await apiFetch(endpoint(`/api/v1/users/${userId}/status`), jwt, {
        method: 'PUT', body: JSON.stringify({ status: newStatus, reason: 'Updated via portal' })
      });
      setEmployees(prev => prev.map(emp => emp.id === userId ? { ...emp, ...updated } : emp));
      showToast(`User ${newStatus.toLowerCase()}d.`);
    } catch (err) { showToast(err.message, 'error'); }
    finally { setLoading(false); }
  };

  const handleChangeRole = async (userId, roleId) => {
    if (!jwt || !roleId) return;
    setLoading(true);
    try {
      const updated = await apiFetch(endpoint(`/api/v1/users/${userId}/role`), jwt, {
        method: 'PUT',
        body: JSON.stringify({ roleId })
      });
      setEmployees(prev => prev.map(emp => emp.id === userId ? { ...emp, ...updated } : emp));
      showToast('Role updated.');
    } catch (err) { showToast(err.message, 'error'); }
    finally { setLoading(false); }
  };

  const handleCreateDepartment = async (deptData) => {
    if (!jwt || !user) return;
    setLoading(true);
    try {
      const created = await apiFetch(endpoint('/api/v1/departments'), jwt, {
        method: 'POST', body: JSON.stringify(deptData)
      });
      setDepartments(prev => [...prev, created]);
      showToast('Department created!');
    } catch (err) { showToast(err.message, 'error'); }
    finally { setLoading(false); }
  };

  const handleCreateRole = async (roleData) => {
    if (!jwt) return;
    setLoading(true);
    try {
      const created = await apiFetch(endpoint('/api/v1/roles'), jwt, {
        method: 'POST',
        body: JSON.stringify(roleData)
      });
      setAvailableRoles(prev => [...prev, created]);
      showToast('Role created!');
    } catch (err) { showToast(err.message, 'error'); }
    finally { setLoading(false); }
  };

  const handleTogglePermission = async (roleId, permission, isAdding) => {
    if (!jwt) return;
    setLoading(true);
    try {
      const updated = await apiFetch(
        endpoint(isAdding ? `/api/v1/roles/${roleId}/permissions` : `/api/v1/roles/${roleId}/permissions/${permission}`),
        jwt,
        isAdding
          ? { method: 'POST', body: JSON.stringify({ permission }) }
          : { method: 'DELETE' }
      );
      setAvailableRoles(prev => prev.map(role => role.id === roleId ? updated : role));
      showToast('Permissions updated.');
    } catch (err) { showToast(err.message, 'error'); }
    finally { setLoading(false); }
  };

  const handleSaveProfile = async (profileData) => {
    if (!jwt || !user) return;
    setLoading(true);
    try {
      const updated = await apiFetch(endpoint('/api/v1/users/me'), jwt, {
        method: 'PUT', body: JSON.stringify(profileData)
      });
      setUser(prev => ({ ...prev, ...updated, role: updated.roleNames?.[0] || prev?.role }));
      showToast('Profile saved!');
    } catch (err) { showToast(err.message, 'error'); }
    finally { setLoading(false); }
  };

  const handleExportAnalytics = async (range = dateRange) => {
    if (!jwt || !user) return;
    try {
      const res = await fetch(
        endpoint(`/api/v1/analytics/export?format=pdf&dateRange=${range}`),
        { headers: { 'Authorization': `Bearer ${jwt}` } }
      );
      if (!res.ok) throw new Error('Export failed');
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url; a.download = `report_${range}_${new Date().toISOString().split('T')[0]}.pdf`; a.click();
      URL.revokeObjectURL(url);
      showToast('Report exported!');
    } catch (err) { showToast(err.message, 'error'); }
  };

  const logout = () => {
    setJwt(''); setUser(null); setBusiness(null);
    setTasks([]); setEmployees([]); setDepartments([]);
    setAvailableRoles([]); setAnalytics(null); setTelegramStatus(null);
    sessionStorage.removeItem('jwt_token');
    setPhase('start');
  };

  return (
    <main className="telegram-canvas">
      <section className="telegram-app">
        <AppHeader phase={phase} screen={screen} loading={loading} onLogout={logout} user={user} />

        {phase === 'start' && (
          <LoginScreen
            loginMethod={loginMethod} setLoginMethod={setLoginMethod}
            emailInput={emailInput} setEmailInput={setEmailInput}
            businessNameInput={businessNameInput} setBusinessNameInput={setBusinessNameInput}
            passwordInput={passwordInput} setPasswordInput={setPasswordInput}
            phoneInput={phoneInput} setPhoneInput={setPhoneInput}
            inviteTokenInput={inviteTokenInput} setInviteTokenInput={setInviteTokenInput}
            loading={loading}
            onPortalLogin={handlePortalLogin}
            onTokenLogin={handleTokenLogin}
          />
        )}

        {phase === 'dashboard' && (
          <Dashboard
            user={user} role={role} screen={screen} setScreen={setScreen}
            tasks={tasks} employees={employees} departments={departments}
            business={business} availableRoles={availableRoles}
            analytics={analytics} telegramStatus={telegramStatus}
            onTaskAction={handleTaskAction} onCreateTask={handleCreateTask}
            onInviteUser={handleInviteUser} onUserStatusChange={handleUserStatusChange}
            onChangeRole={handleChangeRole}
            onCreateDepartment={handleCreateDepartment} onSaveProfile={handleSaveProfile}
            onCreateRole={handleCreateRole} onTogglePermission={handleTogglePermission}
            onExportAnalytics={handleExportAnalytics}
            dateRange={dateRange} setDateRange={setDateRange}
            onRefresh={refresh} jwt={jwt}
          />
        )}
      </section>

      {toast.msg && (
        <button
          className={`toast ${toast.type === 'error' ? 'toast-error' : ''}`}
          onClick={() => setToast({ msg: '', type: 'success' })}
          id="toast-notification"
        >
          <Icon name={toast.type === 'error' ? 'AlertCircle' : 'CheckCircle2'} size={16} />
          <span>{toast.msg}</span>
        </button>
      )}
    </main>
  );
}

// ─── App Header ───────────────────────────────────────────────────────────────

function AppHeader({ phase, screen, loading, onLogout, user }) {
  const title = phase === 'dashboard' ? (navItems.find(i => i.id === screen)?.label || 'Dashboard') : 'AgentCraft';
  return (
    <header className="tg-header">
      <button className="icon-button" onClick={phase === 'dashboard' ? onLogout : undefined} aria-label="Back" id="header-back-btn">
        <Icon name={phase === 'dashboard' ? 'LogOut' : 'ChevronLeft'} />
      </button>
      <div>
        <strong>{title}</strong>
        <span>{phase === 'dashboard' && user ? user.displayName : 'AI Business Portal'}</span>
      </div>
      <button className="icon-button" aria-label="Bot status" id="header-bot-btn">
        {loading
          ? <Icon name="Loader2" size={18} className="spin" />
          : <Icon name="Bot" />}
      </button>
    </header>
  );
}

// ─── Login Screen ─────────────────────────────────────────────────────────────

function LoginScreen({
  loginMethod, setLoginMethod, emailInput, setEmailInput,
  businessNameInput, setBusinessNameInput, passwordInput, setPasswordInput,
  phoneInput, setPhoneInput, inviteTokenInput, setInviteTokenInput,
  loading, onPortalLogin, onTokenLogin
}) {
  return (
    <section className="screen start-screen" id="login-screen">
      <div className="bot-message">
        <Icon name="Bot" size={22} />
        <div>
          <p><strong>Welcome to AgentCraft!</strong></p>
          <p>Your AI-powered Business Management Portal. Sign in to get started.</p>
        </div>
      </div>

      <section className="panel compact" id="login-panel">
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '6px', marginBottom: '18px' }}>
          <button
            type="button" id="login-tab-portal"
            className={loginMethod === 'portal' ? 'primary' : ''}
            onClick={() => setLoginMethod('portal')}
            style={{ padding: '8px', borderRadius: '8px', fontSize: '13px' }}
          >
            🔑 Portal Login
          </button>
          <button
            type="button" id="login-tab-phone"
            className={loginMethod === 'phone' ? 'primary' : ''}
            onClick={() => setLoginMethod('phone')}
            style={{ padding: '8px', borderRadius: '8px', fontSize: '13px' }}
          >
            📱 Phone / Token
          </button>
        </div>

        {loginMethod === 'portal' ? (
          <form onSubmit={onPortalLogin} className="action-stack" id="portal-login-form">
            <label>
              <span>Email Address *</span>
              <input id="login-email" type="email" required placeholder="you@company.com"
                value={emailInput} onChange={e => setEmailInput(e.target.value)} />
            </label>
            <label>
              <span>Company Name *</span>
              <input id="login-business" type="text" required placeholder="e.g. Acme Corp"
                value={businessNameInput} onChange={e => setBusinessNameInput(e.target.value)} />
            </label>
            <label>
              <span>Password *</span>
              <input id="login-password" type="password" required placeholder="••••••••"
                value={passwordInput} onChange={e => setPasswordInput(e.target.value)} />
            </label>
            <button type="submit" id="login-submit-btn" className="primary" style={{ marginTop: '12px' }} disabled={loading}>
              {loading ? 'Authenticating...' : '→ Login to Workspace'}
            </button>
          </form>
        ) : (
          <form onSubmit={onTokenLogin} className="action-stack" id="token-login-form">
            <label>
              <span>Phone Number</span>
              <input id="login-phone" type="tel" placeholder="+15550001234"
                value={phoneInput} onChange={e => setPhoneInput(e.target.value)} />
            </label>
            <div style={{ textAlign: 'center', color: 'var(--muted)', fontSize: '12px' }}>— OR —</div>
            <label>
              <span>Invite Token</span>
              <input id="login-invite-token" type="text" placeholder="Paste your invite token here"
                value={inviteTokenInput} onChange={e => setInviteTokenInput(e.target.value)} />
            </label>
            <button type="submit" id="token-submit-btn" className="primary" style={{ marginTop: '12px' }} disabled={loading}>
              {loading ? 'Verifying...' : '→ Verify & Access Portal'}
            </button>
          </form>
        )}
      </section>
    </section>
  );
}

// ─── Dashboard Shell ──────────────────────────────────────────────────────────

function Dashboard({
  user, role, screen, setScreen,
  tasks, employees, departments, business, availableRoles,
  analytics, telegramStatus, onTaskAction, onCreateTask,
  onInviteUser, onUserStatusChange, onCreateDepartment,
  onChangeRole, onSaveProfile, onCreateRole, onTogglePermission,
  onExportAnalytics, dateRange, setDateRange, onRefresh, jwt
}) {
  const visibleNav = navItems.filter(item => item.roles.includes(role));
  return (
    <section className="dashboard">
      <div className="role-row">
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <span className="badge primary">{role}</span>
          {business && <span style={{ fontSize: '13px', color: 'var(--muted)' }}>{business.name}</span>}
        </div>
        <button id="refresh-btn" onClick={onRefresh} style={{ fontSize: '12px' }}>
          <Icon name="RefreshCw" size={14} /> Refresh
        </button>
      </div>

      <div className="content-shell">
        {screen === 'overview'    && <OverviewScreen tasks={tasks} business={business} employees={employees} departments={departments} analytics={analytics} />}
        {screen === 'analytics'   && <AnalyticsScreen analytics={analytics} tasks={tasks} dateRange={dateRange} setDateRange={setDateRange} onExport={onExportAnalytics} />}
        {screen === 'tasks'       && <TasksScreen role={role} tasks={tasks} employees={employees} onTaskAction={onTaskAction} onCreateTask={onCreateTask} userId={user?.id} jwt={jwt} />}
        {screen === 'employees'   && <DirectoryScreen employees={employees} availableRoles={availableRoles} departments={departments} onInviteUser={onInviteUser} onUserStatusChange={onUserStatusChange} onChangeRole={onChangeRole} />}
        {screen === 'departments' && <DepartmentsScreen departments={departments} availableRoles={availableRoles} onCreateDepartment={onCreateDepartment} onCreateRole={onCreateRole} onTogglePermission={onTogglePermission} />}
        {screen === 'profile'     && <ProfileScreen user={user} telegramStatus={telegramStatus} onSaveProfile={onSaveProfile} jwt={jwt} />}
      </div>

      <nav className="bottom-nav" id="bottom-nav">
        {visibleNav.map(item => (
          <button
            key={item.id}
            id={`nav-${item.id}`}
            className={screen === item.id ? 'active' : ''}
            onClick={() => setScreen(item.id)}
          >
            <Icon name={item.icon} size={17} />
            <span>{item.label}</span>
          </button>
        ))}
      </nav>
    </section>
  );
}

// ─── Overview Screen ──────────────────────────────────────────────────────────

function OverviewScreen({ tasks, business, employees, departments, analytics }) {
  const openTasks      = tasks.filter(t => ['ASSIGNED','REJECTED','IN_PROGRESS'].includes(t.status)).length;
  const pendingApprove = tasks.filter(t => t.status === 'SUBMITTED').length;
  const completedTasks = tasks.filter(t => ['APPROVED','COMPLETED','CLOSED'].includes(t.status)).length;
  const completionPct  = analytics?.completionRate ?? (tasks.length > 0 ? Math.round(completedTasks * 100 / tasks.length) : 0);
  const taskTotal = analytics?.totalTasks ?? tasks.length;
  const employeeTotal = analytics?.totalEmployees ?? employees.length;
  const departmentTotal = analytics?.totalDepartments ?? departments.length;

  return (
    <>
      <div className="metric-grid" id="overview-metrics">
        <Metric id="metric-open-tasks"      title="Tasks"           value={taskTotal}        desc={`${openTasks} open, ${pendingApprove} pending`} accent="primary" icon="ClipboardCheck" />
        <Metric id="metric-team-members"    title="Employees"       value={employeeTotal}    desc="People in workspace" accent="success" icon="UsersRound" />
        <Metric id="metric-departments"     title="Departments"     value={departmentTotal}  desc="Operating groups" accent="warning" icon="Building2" />
        <Metric id="metric-completion-rate" title="Completion Rate" value={`${Number(completionPct).toFixed(0)}%`} desc="Tasks done" accent="info" icon="TrendingUp" />
      </div>

      <Section title="Company Workspace" id="overview-workspace-section">
        <div className="resource-grid" style={{ marginTop: '14px' }}>
          <WorkspaceCard icon="ClipboardCheck" label="Tasks Workspace"   color="#e9f4ff" iconColor="#2688d9" />
          <WorkspaceCard icon="UsersRound"     label="Team Directory"    color="#edf8f1" iconColor="#238a5b" />
          <WorkspaceCard icon="Bot"            label="Telegram AI Bot"   color="#f5f0ff" iconColor="#7c3aed" />
          <WorkspaceCard icon="ShieldCheck"    label="Active Security"   color="#fff4e8" iconColor="#ad741d" />
        </div>
        {business && (
          <div style={{ marginTop: '14px', padding: '12px 14px', borderRadius: '10px', background: 'var(--surface-soft)', fontSize: '13px', color: 'var(--muted)' }}>
            <strong style={{ color: 'var(--text)' }}>{business.name}</strong>
            {business.industry && <span style={{ marginLeft: '8px' }}>· {business.industry}</span>}
            {business.type && <span style={{ marginLeft: '8px' }}>· {business.type}</span>}
          </div>
        )}
      </Section>

      {tasks.length > 0 && (
        <Section title="Recent Activity" id="overview-recent-section">
          <div style={{ marginTop: '10px', display: 'grid', gap: '8px' }}>
            {tasks.slice(0, 5).map(task => (
              <div key={task.id} style={{ display: 'flex', alignItems: 'center', gap: '10px', padding: '10px 12px', borderRadius: '10px', background: 'var(--surface-soft)', border: '1px solid var(--border)' }}>
                <span className={`badge ${task.priority?.toLowerCase()}`} style={{ minWidth: '60px', justifyContent: 'center' }}>{task.priority}</span>
                <span style={{ flex: 1, fontSize: '13px', fontWeight: 600 }}>
                  <em>{task.title}</em> &mdash; Assigned to {task.assignee || 'Unassigned'} (Due: {formatDate(task.dueDate)})
                </span>
                <span className={`badge ${task.status?.toLowerCase()}`}>{task.status}</span>
              </div>
            ))}
          </div>
        </Section>
      )}
    </>
  );
}

function WorkspaceCard({ icon, label, color, iconColor }) {
  return (
    <article style={{ display: 'flex', alignItems: 'center', gap: '10px', padding: '14px', borderRadius: '12px', background: color, border: '1px solid rgba(0,0,0,0.06)' }}>
      <span style={{ color: iconColor }}><Icon name={icon} size={18} /></span>
      <span style={{ fontSize: '13px', fontWeight: 600 }}>{label}</span>
    </article>
  );
}

// ─── Analytics Screen ─────────────────────────────────────────────────────────

function AnalyticsScreen({ analytics, tasks, dateRange, setDateRange, onExport }) {
  const completed = analytics?.completedTasks ?? tasks.filter(t => ['APPROVED','COMPLETED','CLOSED'].includes(t.status)).length;
  const open      = tasks.filter(t => ['ASSIGNED','IN_PROGRESS','OPEN'].includes(t.status)).length;
  const submitted = tasks.filter(t => t.status === 'SUBMITTED').length;
  const rejected  = tasks.filter(t => t.status === 'REJECTED').length;
  const total     = tasks.length;
  const highPri   = tasks.filter(t => t.priority === 'HIGH' || t.priority === 'CRITICAL').length;
  const pct       = analytics?.completionRate ?? (total > 0 ? Math.round(completed * 100 / total) : 0);

  const barData = [
    { label: 'Completed', value: completed, color: '#238a5b' },
    { label: 'Open',      value: open,      color: '#2688d9' },
    { label: 'Submitted', value: submitted, color: '#ad741d' },
    { label: 'Rejected',  value: rejected,  color: '#c43c35' },
  ];
  const maxVal = Math.max(...barData.map(d => d.value), 1);

  return (
    <>
      <Section title="Analytics" id="analytics-section">
        <div style={{ display: 'flex', gap: '10px', marginTop: '14px', flexWrap: 'wrap', alignItems: 'flex-end' }}>
          <label style={{ gap: '4px' }}>
            <span>Date Range</span>
            <select id="analytics-date-range" value={dateRange} onChange={e => setDateRange(e.target.value)} style={{ minHeight: '36px', padding: '6px 10px' }}>
              <option value="daily">Daily</option>
              <option value="weekly">Weekly</option>
              <option value="monthly">Monthly</option>
              <option value="quarterly">Quarterly</option>
              <option value="yearly">Yearly</option>
            </select>
          </label>
          <button
            id="export-report-btn"
            onClick={() => onExport(dateRange)}
            style={{ marginBottom: '0' }}
          >
            <Icon name="Download" size={15} /> Export Report
          </button>
        </div>

        <div style={{ marginTop: '20px' }}>
          <div style={{ display: 'flex', alignItems: 'end', gap: '16px', height: '180px', padding: '16px', borderRadius: '12px', background: 'var(--surface-soft)' }}>
            {barData.map(bar => (
              <div key={bar.label} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', height: '100%', justifyContent: 'flex-end', gap: '6px' }}>
                <span style={{ fontSize: '12px', fontWeight: 700, color: bar.color }}>{bar.value}</span>
                <div style={{ width: '100%', borderRadius: '6px 6px 0 0', background: bar.color, height: `${(bar.value / maxVal) * 80}%`, minHeight: bar.value > 0 ? '4px' : '0', transition: 'height 0.5s ease' }} />
                <span style={{ fontSize: '11px', color: 'var(--muted)', textAlign: 'center' }}>{bar.label}</span>
              </div>
            ))}
          </div>
        </div>
      </Section>

      <div className="metric-grid" style={{ marginTop: '14px' }} id="analytics-kpi-grid">
        <Metric id="kpi-completed"  title="Completed"    value={completed} desc="Successfully done" accent="success" />
        <Metric id="kpi-open"       title="Open Tasks"   value={open}      desc="In progress" accent="primary" />
        <Metric id="kpi-high-pri"   title="High Priority" value={highPri}  desc="HIGH + CRITICAL" accent="warning" />
        <Metric id="kpi-completion" title="Completion %"  value={`${pct}%`} desc="Of all tasks" accent="info" />
      </div>

      {analytics && (
        <Section title="API Analytics" id="api-analytics-section">
          <div className="metric-grid" style={{ marginTop: '14px' }}>
            {analytics.totalTasks  != null && <Metric id="api-kpi-open"   title="Total Tasks"      value={analytics.totalTasks}    desc={analytics.period || dateRange} accent="primary" />}
            {analytics.completedTasks != null && <Metric id="api-kpi-done" title="Completed"       value={analytics.completedTasks} desc="" accent="success" />}
            {analytics.overdueCount!= null && <Metric id="api-kpi-overdue" title="Overdue"          value={analytics.overdueCount} desc="" accent="warning" />}
            {analytics.averageTaskTime != null && <Metric id="api-kpi-avg" title="Avg. Completion" value={`${analytics.averageTaskTime}h`} desc="Per task" accent="info" />}
          </div>
          {analytics.departmentMetrics?.length > 0 && (
            <div style={{ marginTop: '14px', display: 'grid', gap: '8px' }}>
              {analytics.departmentMetrics.map(metric => (
                <div key={metric.departmentId || metric.department} style={{ display: 'grid', gap: '5px' }}>
                  <strong style={{ fontSize: '12px' }}>{metric.department}</strong>
                  <div style={{ height: '9px', borderRadius: '99px', background: 'var(--surface-soft)', border: '1px solid var(--border)', overflow: 'hidden' }}>
                    <span style={{ display: 'block', height: '100%', width: `${Math.min(metric.completionRate || 0, 100)}%`, background: 'var(--success)' }} />
                  </div>
                </div>
              ))}
            </div>
          )}
        </Section>
      )}
    </>
  );
}

// ─── Tasks Screen ─────────────────────────────────────────────────────────────

function TasksScreen({ role, tasks, employees, onTaskAction, onCreateTask, userId, jwt }) {
  const [showForm, setShowForm] = React.useState(false);
  const [filter, setFilter]     = React.useState('ALL');
  const [search, setSearch]     = React.useState('');
  const [title, setTitle]       = React.useState('');
  const [description, setDesc]  = React.useState('');
  const [dueDate, setDueDate]   = React.useState('');
  const [priority, setPriority] = React.useState('MEDIUM');
  const [assigneeId, setAssignee] = React.useState('');
  const [historyTask, setHistoryTask] = React.useState(null);
  const [history, setHistory] = React.useState([]);

  const statusFilters = ['ALL', 'OPEN', 'ASSIGNED', 'IN_PROGRESS', 'SUBMITTED', 'APPROVED', 'REJECTED', 'COMPLETED'];

  const filtered = tasks
    .filter(t => filter === 'ALL' || t.status === filter)
    .filter(t => !search || t.title.toLowerCase().includes(search.toLowerCase()));

  const submitTask = (e) => {
    e.preventDefault();
    if (!title.trim()) return;
    onCreateTask({
      title: title.trim(), description, priority,
      dueDate: dueDate ? dueDate + 'T23:59:59Z' : null,
      assigneeId: assigneeId || null
    });
    setShowForm(false); setTitle(''); setDesc(''); setDueDate(''); setPriority('MEDIUM'); setAssignee('');
  };

  const isManager = role !== 'Employee';
  const openHistory = async (task) => {
    setHistoryTask(task);
    setHistory([]);
    try {
      const data = await apiFetch(endpoint(`/api/v1/tasks/${task.id}/history`), jwt);
      setHistory(unwrapList(data));
    } catch {
      setHistory([]);
    }
  };

  return (
    <Section title="Task Management" action={isManager ? (showForm ? 'Cancel' : '+ Create Task') : ''} onActionClick={() => setShowForm(!showForm)} id="tasks-section">
      {showForm && (
        <form onSubmit={submitTask} className="panel" style={{ marginTop: '14px', display: 'grid', gap: '10px' }} id="create-task-form">
          <label><span>Task Title *</span><input id="task-title-input" type="text" required value={title} onChange={e => setTitle(e.target.value)} /></label>
          <label><span>Description</span><textarea id="task-desc-input" value={description} onChange={e => setDesc(e.target.value)} rows={3} /></label>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
            <label><span>Due Date</span><input id="task-due-input" type="date" value={dueDate} onChange={e => setDueDate(e.target.value)} /></label>
            <label>
              <span>Priority</span>
              <select id="task-priority-input" value={priority} onChange={e => setPriority(e.target.value)}>
                <option value="LOW">Low</option>
                <option value="MEDIUM">Medium</option>
                <option value="HIGH">High</option>
                <option value="CRITICAL">Critical</option>
              </select>
            </label>
          </div>
          <label>
            <span>Assignee</span>
            <select id="task-assignee-input" value={assigneeId} onChange={e => setAssignee(e.target.value)}>
              <option value="">— Unassigned —</option>
              {employees.map(emp => (
                <option key={emp.id} value={emp.id}>{emp.displayName} ({emp.primaryPhone || emp.email || 'No contact'})</option>
              ))}
            </select>
          </label>
          <button type="submit" id="task-submit-btn" className="primary" style={{ marginTop: '6px' }}>✓ Create & Assign Task</button>
        </form>
      )}

      <div style={{ display: 'flex', gap: '8px', margin: '14px 0 10px', flexWrap: 'wrap' }}>
        {statusFilters.map(f => (
          <button
            key={f} id={`task-filter-${f.toLowerCase()}`}
            className={filter === f ? 'primary' : ''}
            onClick={() => setFilter(f)}
            style={{ fontSize: '11px', padding: '5px 10px', minHeight: '28px' }}
          >
            {f}
          </button>
        ))}
      </div>

      <div style={{ position: 'relative', marginBottom: '10px' }}>
        <Icon name="Search" size={15} style={{ position: 'absolute', left: '10px', top: '50%', transform: 'translateY(-50%)', color: 'var(--muted)' }} />
        <input
          id="task-search-input" type="text" placeholder="Search tasks…"
          value={search} onChange={e => setSearch(e.target.value)}
          style={{ paddingLeft: '34px' }}
        />
      </div>

      <DataTable
        id="tasks-table"
        columns={['Task', 'Assignee', 'Priority', 'Due Date', 'Status', 'Actions']}
        data={filtered}
        renderRow={task => (
          <tr key={task.id}>
            <td>
              <button type="button" onClick={() => openHistory(task)} style={{ padding: 0, border: 0, minHeight: 0, background: 'transparent', boxShadow: 'none', color: 'var(--primary)', fontSize: '13px' }}>
                <strong>{task.title}</strong>
              </button>
              {task.description && <p style={{ margin: '3px 0 0', fontSize: '11px', color: 'var(--muted)' }}>{task.description.substring(0, 60)}{task.description.length > 60 ? '…' : ''}</p>}
            </td>
            <td style={{ fontSize: '12px', color: 'var(--muted)' }}>{task.assignee || task.assigneeName || '—'}</td>
            <td><span className={`badge ${task.priority?.toLowerCase()}`}>{task.priority}</span></td>
            <td style={{ fontSize: '12px' }}>{task.dueDate ? task.dueDate.substring(0, 10) : '—'}</td>
            <td><span className={`badge ${task.status?.toLowerCase()}`}>{task.status}</span></td>
            <td>
              <div style={{ display: 'flex', gap: '5px', flexWrap: 'wrap' }}>
                {role === 'Employee' && ['ASSIGNED','REJECTED'].includes(task.status) && (
                  <button id={`task-submit-${task.id}`} onClick={() => onTaskAction(task.id, 'SUBMITTED')} style={{ fontSize: '11px', padding: '4px 8px', background: 'var(--success)', color: '#fff', border: 0, minHeight: '26px' }}>
                    Submit
                  </button>
                )}
                {isManager && task.status === 'SUBMITTED' && (<>
                  <button id={`task-approve-${task.id}`} onClick={() => onTaskAction(task.id, 'APPROVE')} style={{ fontSize: '11px', padding: '4px 8px', background: 'var(--success)', color: '#fff', border: 0, minHeight: '26px' }}>
                    ✓ Approve
                  </button>
                  <button id={`task-reject-${task.id}`} onClick={() => onTaskAction(task.id, 'REJECT', { reason: 'Rejected by manager' })} style={{ fontSize: '11px', padding: '4px 8px', background: 'var(--error)', color: '#fff', border: 0, minHeight: '26px' }}>
                    ✗ Reject
                  </button>
                </>)}
                {isManager && task.status === 'ASSIGNED' && (
                  <button id={`task-reassign-${task.id}`} onClick={() => onTaskAction(task.id, 'OPEN')} style={{ fontSize: '11px', padding: '4px 8px', minHeight: '26px' }}>
                    Reassign
                  </button>
                )}
                {isManager && (
                  <button id={`task-history-btn-${task.id}`} onClick={() => openHistory(task)} style={{ fontSize: '11px', padding: '4px 8px', minHeight: '26px' }}>
                    History
                  </button>
                )}
              </div>
            </td>
          </tr>
        )}
      />

      {/* ── Task History Modal ── */}
      {historyTask && (
        <div
          id="task-history-modal-overlay"
          role="dialog"
          aria-modal="true"
          aria-label="Task history"
          onClick={e => { if (e.target === e.currentTarget) { setHistoryTask(null); setHistory([]); } }}
          style={{
            position: 'fixed', inset: 0, zIndex: 200,
            background: 'rgba(15,23,42,0.5)', backdropFilter: 'blur(4px)',
            display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '20px'
          }}
        >
          <div id="task-history-modal" style={{
            background: 'var(--surface)', borderRadius: 'var(--radius-lg)', padding: '24px',
            maxWidth: '560px', width: '100%', maxHeight: '80vh', overflow: 'auto',
            boxShadow: 'var(--shadow-lg)', border: '1px solid var(--border)'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '18px' }}>
              <h2 style={{ fontSize: '15px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Icon name="Clock" size={16} /> {historyTask.title}
              </h2>
              <button
                id="task-history-close-btn"
                onClick={() => { setHistoryTask(null); setHistory([]); }}
                style={{ padding: '4px 12px', fontSize: '12px', minHeight: '30px' }}
              >✕ Close</button>
            </div>

            {history.length === 0 ? (
              <div className="empty-inline"><Icon name="Clock" size={22} /><span>No history recorded yet.</span></div>
            ) : (
              <div style={{ display: 'grid', gap: '8px' }}>
                {history.map((h, idx) => {
                  const oldStatus = h.oldStatus || h.oldValue?.status;
                  const newStatus = h.newStatus || h.newValue?.status;
                  const note = h.note || h.reason;
                  const changedAt = h.changedAt || h.createdAt;
                  return (
                    <div key={h.id || idx} style={{
                      display: 'flex', gap: '12px', padding: '12px 14px',
                      borderRadius: '10px', background: 'var(--surface-soft)', border: '1px solid var(--border)'
                    }}>
                      <div style={{ flexShrink: 0, width: '8px', height: '8px', borderRadius: '50%', background: 'var(--primary)', marginTop: '7px' }} />
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', gap: '8px', flexWrap: 'wrap', marginBottom: '5px' }}>
                          <span className="badge primary" style={{ fontSize: '10px' }}>{h.action}</span>
                          <span style={{ fontSize: '11px', color: 'var(--muted)' }}>{changedAt ? formatDate(changedAt) : ''}</span>
                        </div>
                        {(oldStatus || newStatus) && (
                          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '4px' }}>
                            {oldStatus && <span className={`badge ${oldStatus.toLowerCase()}`}>{oldStatus}</span>}
                            {oldStatus && newStatus && <span style={{ color: 'var(--muted)', fontSize: '12px' }}>→</span>}
                            {newStatus && <span className={`badge ${newStatus.toLowerCase()}`}>{newStatus}</span>}
                          </div>
                        )}
                        {note && <p style={{ fontSize: '12px', color: 'var(--muted)', marginTop: '3px', wordBreak: 'break-word' }}>{note}</p>}
                        {h.changedBy && <p style={{ fontSize: '11px', color: 'var(--muted)', marginTop: '3px' }}>by {h.changedBy}</p>}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      )}
    </Section>
  );
}

// ─── Employee Directory Screen ────────────────────────────────────────────────

function DirectoryScreen({ employees, availableRoles, departments, onInviteUser, onUserStatusChange, onChangeRole }) {
  const [showInvite, setShowInvite] = React.useState(false);
  const [phone, setPhone]           = React.useState('');
  const [displayName, setName]      = React.useState('');
  const [roleId, setRoleId]         = React.useState('');
  const [deptId, setDeptId]         = React.useState('');
  const [search, setSearch]         = React.useState('');
  const [statusFilter, setStatusFilter] = React.useState('ALL');

  const filteredEmps = employees
    .filter(e => statusFilter === 'ALL' || e.status === statusFilter)
    .filter(e => !search || e.displayName?.toLowerCase().includes(search.toLowerCase()) || e.email?.toLowerCase().includes(search.toLowerCase()));

  const submitInvite = (e) => {
    e.preventDefault();
    if (!phone.trim() || !roleId) return;
    onInviteUser({ phoneNumber: phone.trim(), displayName: displayName || null, roleId, departmentId: deptId || null });
    setShowInvite(false); setPhone(''); setName(''); setRoleId(''); setDeptId('');
  };

  return (
    <Section title="Employee Directory" action={showInvite ? 'Cancel' : '+ Invite Employee'} onActionClick={() => { setShowInvite(!showInvite); if (availableRoles.length && !roleId) setRoleId(availableRoles[0].id); }} id="employees-section">
      {showInvite && (
        <form onSubmit={submitInvite} className="panel" style={{ marginTop: '14px', display: 'grid', gap: '10px' }} id="invite-user-form">
          <label><span>Phone Number *</span><input id="invite-phone" type="tel" required placeholder="+15550001234" value={phone} onChange={e => setPhone(e.target.value)} /></label>
          <label><span>Display Name</span><input id="invite-name" type="text" placeholder="Full name (optional)" value={displayName} onChange={e => setName(e.target.value)} /></label>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
            <label>
              <span>Role *</span>
              <select id="invite-role" required value={roleId} onChange={e => setRoleId(e.target.value)}>
                <option value="">— Select role —</option>
                {availableRoles.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}
              </select>
            </label>
            <label>
              <span>Department</span>
              <select id="invite-dept" value={deptId} onChange={e => setDeptId(e.target.value)}>
                <option value="">— No department —</option>
                {departments.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}
              </select>
            </label>
          </div>
          <button type="submit" id="invite-submit-btn" className="primary" style={{ marginTop: '6px' }}>📨 Send Invitation Link</button>
        </form>
      )}

      <div style={{ display: 'flex', gap: '10px', margin: '14px 0 10px', flexWrap: 'wrap', alignItems: 'center' }}>
        <div style={{ position: 'relative', flex: 1 }}>
          <Icon name="Search" size={15} style={{ position: 'absolute', left: '10px', top: '50%', transform: 'translateY(-50%)', color: 'var(--muted)' }} />
          <input id="emp-search" type="text" placeholder="Search employees…" value={search} onChange={e => setSearch(e.target.value)} style={{ paddingLeft: '34px' }} />
        </div>
        <select id="emp-status-filter" value={statusFilter} onChange={e => setStatusFilter(e.target.value)} style={{ minHeight: '42px', maxWidth: '150px' }}>
          <option value="ALL">All Statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="PENDING">Pending</option>
          <option value="SUSPENDED">Suspended</option>
        </select>
      </div>

      <DataTable
        id="employees-table"
        columns={['Name', 'Contact', 'Role', 'Department', 'Status', 'Actions']}
        data={filteredEmps}
        renderRow={emp => (
          <tr key={emp.id}>
            <td><strong>{emp.displayName}</strong></td>
            <td style={{ fontSize: '12px' }}>
              {emp.email && <div>{emp.email}</div>}
              {emp.primaryPhone && <div style={{ color: 'var(--muted)' }}>{emp.primaryPhone}</div>}
            </td>
            <td>
              {onChangeRole && availableRoles.length > 0 ? (
                <select
                  id={`emp-role-select-${emp.id}`}
                  value={(emp.roleIds || [])[0] || ''}
                  onChange={e => e.target.value && onChangeRole(emp.id, e.target.value)}
                  style={{ minHeight: '28px', padding: '3px 6px', fontSize: '12px', width: 'auto', minWidth: '100px' }}
                >
                  <option value="">— Role —</option>
                  {availableRoles.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}
                </select>
              ) : (
                (emp.roleNames || []).map(r => <span key={r} className="badge" style={{ marginRight: '3px' }}>{r}</span>)
              )}
            </td>
            <td style={{ fontSize: '12px', color: 'var(--muted)' }}>{emp.department || emp.departmentName || emp.departmentNames?.[0] || '—'}</td>
            <td><span className={`badge ${emp.status?.toLowerCase()}`}>{emp.status}</span></td>
            <td>
              <div style={{ display: 'flex', gap: '5px' }}>
                {emp.status === 'ACTIVE' && (
                  <button id={`emp-suspend-${emp.id}`} onClick={() => onUserStatusChange(emp.id, 'SUSPENDED')} style={{ fontSize: '11px', padding: '4px 8px', minHeight: '26px', background: 'var(--error)', color: '#fff', border: 0 }}>
                    Suspend
                  </button>
                )}
                {(emp.status === 'SUSPENDED' || emp.status === 'PENDING') && (
                  <button id={`emp-activate-${emp.id}`} onClick={() => onUserStatusChange(emp.id, 'ACTIVE')} style={{ fontSize: '11px', padding: '4px 8px', minHeight: '26px', background: 'var(--success)', color: '#fff', border: 0 }}>
                    Activate
                  </button>
                )}
              </div>
            </td>
          </tr>
        )}
      />
    </Section>
  );
}

// ─── Departments Screen ───────────────────────────────────────────────────────

const ALL_PERMISSIONS = [
  'TASK_CREATE', 'TASK_EDIT', 'TASK_DELETE', 'TASK_APPROVE', 'TASK_VIEW_ALL', 'TASK_VIEW_OWN', 'TASK_COMPLETE',
  'USER_INVITE', 'USER_EDIT', 'USER_DELETE', 'USER_SUSPEND', 'USER_VIEW', 'USER_MANAGE',
  'DEPARTMENT_CREATE', 'DEPARTMENT_EDIT', 'DEPARTMENT_DELETE', 'DEPT_MANAGE',
  'ROLE_CREATE', 'ROLE_EDIT', 'ROLE_DELETE', 'ROLE_MANAGE',
  'ANALYTICS_VIEW', 'SETTINGS_EDIT', 'REPORT_VIEW', 'REPORT_EXPORT',
];

function DepartmentsScreen({ departments, availableRoles, onCreateDepartment, onCreateRole, onTogglePermission }) {
  const [showDeptForm, setShowDeptForm] = React.useState(false);
  const [showRoleForm, setShowRoleForm] = React.useState(false);
  const [deptName, setDeptName] = React.useState('');
  const [deptDesc, setDeptDesc] = React.useState('');
  const [roleName, setRoleName] = React.useState('');
  const [rolePerms, setRolePerms] = React.useState([]);
  const [expandedRole, setExpandedRole] = React.useState(null);

  const submitDept = (e) => {
    e.preventDefault();
    if (!deptName.trim()) return;
    onCreateDepartment({ name: deptName.trim(), description: deptDesc });
    setShowDeptForm(false); setDeptName(''); setDeptDesc('');
  };

  const submitRole = (e) => {
    e.preventDefault();
    if (!roleName.trim()) return;
    onCreateRole({ name: roleName.trim(), permissions: rolePerms });
    setShowRoleForm(false); setRoleName(''); setRolePerms([]);
  };

  const toggleRolePerm = (perm) => {
    setRolePerms(prev => prev.includes(perm) ? prev.filter(p => p !== perm) : [...prev, perm]);
  };

  return (
    <>
      {/* ── Departments ── */}
      <Section title="Departments" action={showDeptForm ? 'Cancel' : '+ Add Department'} onActionClick={() => setShowDeptForm(!showDeptForm)} id="departments-section">
        {showDeptForm && (
          <form onSubmit={submitDept} className="panel" style={{ marginTop: '14px', display: 'grid', gap: '10px' }} id="create-dept-form">
            <label><span>Department Name *</span><input id="dept-name-input" type="text" required placeholder="e.g. Marketing" value={deptName} onChange={e => setDeptName(e.target.value)} /></label>
            <label><span>Description (optional)</span><textarea id="dept-desc-input" rows={2} value={deptDesc} onChange={e => setDeptDesc(e.target.value)} /></label>
            <button type="submit" id="dept-submit-btn" className="primary" style={{ marginTop: '6px' }}>🏢 Create Department</button>
          </form>
        )}

        <div style={{ marginTop: '14px', display: 'grid', gap: '10px' }} id="departments-list">
          {departments.length === 0 && (
            <div className="empty-inline" style={{ padding: '32px' }}>
              <Icon name="Building2" size={28} />
              <div><strong>No departments yet.</strong><p style={{ margin: '4px 0 0', color: 'var(--muted)', fontSize: '13px' }}>Click "+ Add Department" to get started.</p></div>
            </div>
          )}
          {departments.map((dept, i) => (
            <div key={dept.id} style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '14px 16px', borderRadius: '12px', background: 'var(--surface-soft)', border: '1px solid var(--border)' }}>
              <div style={{ width: '36px', height: '36px', borderRadius: '10px', background: '#e9f4ff', color: '#2688d9', display: 'grid', placeItems: 'center', fontSize: '16px', fontWeight: 700, flexShrink: 0 }}>
                {i + 1}
              </div>
              <div style={{ flex: 1 }}>
                <strong style={{ fontSize: '14px' }}>{dept.name}</strong>
                {dept.description && <p style={{ margin: '2px 0 0', fontSize: '12px', color: 'var(--muted)' }}>{dept.description}</p>}
              </div>
              <span className="badge">{dept.memberCount || 0} members</span>
            </div>
          ))}
        </div>
      </Section>

      {/* ── Roles ── */}
      <Section title="Roles & Permissions" action={showRoleForm ? 'Cancel' : '+ Create Role'} onActionClick={() => setShowRoleForm(!showRoleForm)} id="roles-section">
        {showRoleForm && (
          <form onSubmit={submitRole} className="panel" style={{ marginTop: '14px', display: 'grid', gap: '12px' }} id="create-role-form">
            <label><span>Role Name *</span><input id="role-name-input" type="text" required placeholder="e.g. Senior Auditor" value={roleName} onChange={e => setRoleName(e.target.value)} /></label>
            <div>
              <span style={{ fontSize: '12px', fontWeight: 700, color: 'var(--muted)', textTransform: 'uppercase', letterSpacing: '0.04em', display: 'block', marginBottom: '10px' }}>Permissions</span>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))', gap: '6px' }}>
                {ALL_PERMISSIONS.map(perm => (
                  <label key={perm} style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', padding: '6px 8px', borderRadius: '8px', background: rolePerms.includes(perm) ? 'var(--primary-light)' : 'var(--surface-soft)', border: '1px solid var(--border)', userSelect: 'none' }}>
                    <input
                      type="checkbox"
                      checked={rolePerms.includes(perm)}
                      onChange={() => toggleRolePerm(perm)}
                      style={{ width: 'auto', minHeight: 'auto', cursor: 'pointer' }}
                      id={`new-role-perm-${perm}`}
                    />
                    <span style={{ fontSize: '11px', fontWeight: 600, color: rolePerms.includes(perm) ? 'var(--primary)' : 'var(--text)' }}>{perm}</span>
                  </label>
                ))}
              </div>
            </div>
            <button type="submit" id="role-submit-btn" className="primary" style={{ marginTop: '4px' }}>🔐 Create Role</button>
          </form>
        )}

        <div style={{ marginTop: '14px', display: 'grid', gap: '10px' }} id="roles-list">
          {(availableRoles || []).length === 0 && (
            <div className="empty-inline" style={{ padding: '32px' }}>
              <Icon name="ShieldCheck" size={28} />
              <div><strong>No roles yet.</strong><p style={{ margin: '4px 0 0', color: 'var(--muted)', fontSize: '13px' }}>Create custom roles with specific permissions.</p></div>
            </div>
          )}
          {(availableRoles || []).map(r => (
            <div key={r.id} style={{ borderRadius: '12px', background: 'var(--surface-soft)', border: '1px solid var(--border)', overflow: 'hidden' }}>
              <div
                style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '14px 16px', cursor: 'pointer' }}
                onClick={() => setExpandedRole(expandedRole === r.id ? null : r.id)}
              >
                <Icon name="ShieldCheck" size={18} />
                <div style={{ flex: 1 }}>
                  <strong style={{ fontSize: '14px' }}>{r.name}</strong>
                  {r.isBuiltIn && <span className="badge" style={{ marginLeft: '8px', fontSize: '10px' }}>Built-in</span>}
                  <p style={{ margin: '2px 0 0', fontSize: '11px', color: 'var(--muted)' }}>{(r.permissions || []).length} permission{(r.permissions || []).length !== 1 ? 's' : ''}</p>
                </div>
                <Icon name={expandedRole === r.id ? 'ChevronLeft' : 'ChevronRight'} size={16} style={{ color: 'var(--muted)', transform: expandedRole === r.id ? 'rotate(-90deg)' : 'rotate(90deg)' }} />
              </div>
              {expandedRole === r.id && (
                <div style={{ padding: '14px 16px', borderTop: '1px solid var(--border)', background: 'var(--surface)' }}>
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))', gap: '6px' }}>
                    {ALL_PERMISSIONS.map(perm => {
                      const hasIt = (r.permissions || []).includes(perm);
                      return (
                        <label key={perm} style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: r.isBuiltIn ? 'not-allowed' : 'pointer', padding: '6px 8px', borderRadius: '8px', background: hasIt ? 'var(--primary-light)' : 'var(--surface-soft)', border: '1px solid var(--border)', opacity: r.isBuiltIn ? 0.7 : 1 }}>
                          <input
                            type="checkbox"
                            checked={hasIt}
                            disabled={r.isBuiltIn}
                            onChange={() => !r.isBuiltIn && onTogglePermission && onTogglePermission(r.id, perm, !hasIt)}
                            style={{ width: 'auto', minHeight: 'auto', cursor: r.isBuiltIn ? 'not-allowed' : 'pointer' }}
                            id={`role-${r.id}-perm-${perm}`}
                          />
                          <span style={{ fontSize: '11px', fontWeight: 600, color: hasIt ? 'var(--primary)' : 'var(--muted)' }}>{perm}</span>
                        </label>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      </Section>
    </>
  );
}

// ─── Profile Screen ───────────────────────────────────────────────────────────

function ProfileScreen({ user, telegramStatus, onSaveProfile, jwt }) {
  const [displayName, setDisplayName] = React.useState(user?.displayName || '');
  const [email, setEmail]             = React.useState(user?.email || '');
  const [editMode, setEditMode]       = React.useState(false);
  const [copied, setCopied]           = React.useState(false);
  const [tgRefreshing, setTgRefreshing] = React.useState(false);
  const [tgStatus, setTgStatus]         = React.useState(telegramStatus);

  React.useEffect(() => {
    setDisplayName(user?.displayName || '');
    setEmail(user?.email || '');
  }, [user]);

  React.useEffect(() => {
    setTgStatus(telegramStatus);
  }, [telegramStatus]);

  const handleSave = (e) => {
    e.preventDefault();
    onSaveProfile({ displayName: displayName.trim(), email: email.trim() });
    setEditMode(false);
  };

  const copyBotLink = () => {
    const link = `https://t.me/${tgStatus?.botUsername || 'AgentCraftBot'}`;
    navigator.clipboard.writeText(link).then(() => {
      setCopied(true); setTimeout(() => setCopied(false), 2500);
    });
  };

  const refreshTelegramStatus = async () => {
    if (!jwt || tgRefreshing) return;
    setTgRefreshing(true);
    try {
      const status = await apiFetch(endpoint('/api/v1/telegram/status'), jwt);
      setTgStatus(status);
    } catch (err) {
      console.error('Telegram status refresh failed:', err);
    } finally {
      setTgRefreshing(false);
    }
  };

  const webhookConnected = tgStatus?.webhookActive || tgStatus?.isActive;

  return (
    <Section title="Profile & Settings" id="profile-section">
      {/* ── User Card ── */}
      <div className="profile-card" style={{ marginTop: '14px' }}>
        <div className="avatar" style={{ width: '60px', height: '60px' }}>
          <Icon name="UserRound" size={28} />
        </div>
        <div style={{ flex: 1 }}>
          <h2>{user?.displayName || 'User'}</h2>
          <p style={{ margin: '4px 0 0', color: 'var(--muted)', fontSize: '13px' }}>{user?.email || 'No email'}</p>
          <div className="badge-row">
            <span className="badge primary">{user?.role || 'Employee'}</span>
            {user?.primaryPhone && <span className="badge">{user.primaryPhone}</span>}
            <span className={`badge ${user?.status?.toLowerCase()}`}>{user?.status || 'Active'}</span>
          </div>
        </div>
        <button id="edit-profile-btn" onClick={() => setEditMode(!editMode)} style={{ alignSelf: 'flex-start' }}>
          <Icon name="Edit3" size={15} /> {editMode ? 'Cancel' : 'Edit'}
        </button>
      </div>

      {/* ── Edit Profile Form ── */}
      {editMode && (
        <form onSubmit={handleSave} className="panel" style={{ marginTop: '14px', display: 'grid', gap: '10px' }} id="profile-edit-form">
          <label>
            <span>Display Name</span>
            <input id="profile-name-input" type="text" value={displayName} onChange={e => setDisplayName(e.target.value)} />
          </label>
          <label>
            <span>Email Address</span>
            <input id="profile-email-input" type="email" value={email} onChange={e => setEmail(e.target.value)} />
          </label>
          <button type="submit" id="profile-save-btn" className="primary">💾 Save Profile</button>
        </form>
      )}

      {/* ── Telegram Integration ── */}
      <div style={{ marginTop: '20px', display: 'grid', gap: '12px' }}>
        {/* Bot Link */}
        <div style={{ padding: '14px 16px', borderRadius: '12px', background: 'var(--surface-soft)', border: '1px solid var(--border)' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <Icon name="Bot" size={18} />
              <div>
                <strong style={{ fontSize: '13px' }}>Telegram Bot</strong>
                <p style={{ margin: '2px 0 0', fontSize: '12px', color: 'var(--muted)' }}>@{tgStatus?.botUsername || 'AgentCraftBot'}</p>
              </div>
            </div>
            <button id="copy-bot-link-btn" onClick={copyBotLink} style={{ fontSize: '12px' }}>
              <Icon name={copied ? 'Check' : 'Copy'} size={14} /> {copied ? 'Copied!' : 'Copy Link'}
            </button>
          </div>
        </div>

        {/* Webhook Status */}
        <div style={{ padding: '14px 16px', borderRadius: '12px', background: 'var(--surface-soft)', border: `1.5px solid ${webhookConnected ? '#bfe2cd' : 'var(--border)'}` }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <Icon name={webhookConnected ? 'CheckCircle2' : 'Plug'} size={18} style={{ color: webhookConnected ? 'var(--success)' : 'var(--muted)' }} />
            <div style={{ flex: 1 }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px' }}>
                <strong style={{ fontSize: '13px' }}>Webhook Status</strong>
                <span id="webhook-status-badge" className={`badge ${webhookConnected ? 'success' : 'error'}`}>
                  {webhookConnected ? '🟢 ONLINE' : '🔴 OFFLINE'}
                </span>
              </div>
              <p style={{ margin: '4px 0 0', fontSize: '12px', color: webhookConnected ? 'var(--success)' : 'var(--muted)' }}>
                {tgStatus == null ? 'Checking…' : webhookConnected ? '✓ Webhook connected and receiving updates' : '○ No webhook configured'}
              </p>
              {tgStatus?.webhookUrl && (
                <p id="webhook-url-display" style={{ margin: '4px 0 0', fontSize: '11px', color: 'var(--muted)', wordBreak: 'break-all' }}>
                  🔗 {tgStatus.webhookUrl}
                </p>
              )}
              {tgStatus?.lastActivity && (
                <p id="webhook-last-activity" style={{ margin: '3px 0 0', fontSize: '11px', color: 'var(--muted)' }}>
                  Last activity: {formatDate(tgStatus.lastActivity)}
                </p>
              )}
            </div>
          </div>
          <div style={{ marginTop: '12px', display: 'flex', justifyContent: 'flex-end' }}>
            <button
              id="refresh-telegram-status-btn"
              onClick={refreshTelegramStatus}
              disabled={tgRefreshing}
              style={{ fontSize: '12px' }}
            >
              <Icon name={tgRefreshing ? 'Loader2' : 'RefreshCw'} size={14} className={tgRefreshing ? 'spin' : ''} />
              {tgRefreshing ? 'Refreshing…' : 'Refresh Status'}
            </button>
          </div>
        </div>

        {/* Account Info */}
        <div style={{ padding: '14px 16px', borderRadius: '12px', background: 'var(--surface-soft)', border: '1px solid var(--border)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '10px' }}>
            <Icon name="ShieldCheck" size={18} />
            <strong style={{ fontSize: '13px' }}>Account Security</strong>
          </div>
          <div style={{ display: 'grid', gap: '6px', fontSize: '12px', color: 'var(--muted)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span>User ID</span>
              <span style={{ fontFamily: 'monospace', fontSize: '11px' }}>{user?.id ? String(user.id).substring(0, 16) + '…' : '—'}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span>Account Status</span>
              <span className={`badge ${user?.status?.toLowerCase()}`}>{user?.status || 'Active'}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span>Role</span>
              <span className="badge primary">{user?.role || 'Employee'}</span>
            </div>
          </div>
        </div>
      </div>
    </Section>
  );
}

// ─── Reusable Components ──────────────────────────────────────────────────────

function Section({ title, action, onActionClick, children, id }) {
  return (
    <section className="panel section-panel" id={id}>
      <header className="section-header">
        <h1>{title}</h1>
        {action && <button type="button" id={`${id}-action-btn`} onClick={onActionClick}>{action}</button>}
      </header>
      {children}
    </section>
  );
}

function Metric({ title, value, desc, accent = 'primary', id, icon }) {
  const accentColors = {
    primary: { bg: '#e9f4ff', color: '#2688d9' },
    success: { bg: '#edf8f1', color: '#238a5b' },
    warning: { bg: '#fff4e8', color: '#ad741d' },
    error:   { bg: '#fff1f0', color: '#c43c35' },
    info:    { bg: '#f0f4ff', color: '#4f46e5' },
  };
  const c = accentColors[accent] || accentColors.primary;
  return (
    <article className="metric-card" id={id} style={{ borderLeft: `4px solid ${c.color}` }}>
      <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
        {icon && <Icon name={icon} size={15} />}
        {title}
      </span>
      <strong style={{ color: c.color }}>{value}</strong>
      <p>{desc}</p>
    </article>
  );
}

function DataTable({ columns, data, renderRow, id }) {
  return (
    <div className="table-wrap" id={id}>
      <table>
        <thead>
          <tr>{columns.map(col => <th key={col}>{col}</th>)}</tr>
        </thead>
        <tbody>
          {data && data.length > 0 ? (
            data.map(renderRow)
          ) : (
            <tr>
              <td colSpan={columns.length}>
                <div className="empty-inline"><Icon name="Search" size={20} /><span>No records found.</span></div>
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

createRoot(document.getElementById('root')).render(<App />);
