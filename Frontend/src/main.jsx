import React from 'react';
import { createRoot } from 'react-dom/client';
import {
  AlertCircle,
  BarChart3,
  Bell,
  Bot,
  BookOpen,
  BriefcaseBusiness,
  Check,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  ClipboardCheck,
  Filter,
  Home,
  Loader2,
  LogOut,
  MessageSquare,
  Plug,
  Search,
  Send,
  Settings,
  ShieldCheck,
  UserRound,
  UsersRound,
  Plus
} from 'lucide-react';
import './styles.css';

const iconMap = {
  AlertCircle,
  BarChart3,
  Bell,
  Bot,
  BookOpen,
  BriefcaseBusiness,
  Check,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  ClipboardCheck,
  Filter,
  Home,
  Loader2,
  LogOut,
  MessageSquare,
  Plug,
  Search,
  Send,
  Settings,
  ShieldCheck,
  UserRound,
  UsersRound,
  Plus
};

const Icon = ({ name, size = 18 }) => {
  const LucideIcon = iconMap[name] || CheckCircle2;
  return <LucideIcon size={size} strokeWidth={1.9} aria-hidden="true" />;
};

const roles = ['CEO', 'Manager', 'Lead', 'Employee'];

const setupSteps = [
  'Start',
  'Identity',
  'Business',
  'Telegram',
  'Bot',
  'Verification',
  'Portal'
];

const navItems = [
  { id: 'overview', label: 'Overview', icon: 'Home', roles },
  { id: 'analytics', label: 'Analytics', icon: 'BarChart3', roles: ['CEO', 'Manager', 'Lead'] },
  { id: 'tasks', label: 'Tasks', icon: 'ClipboardCheck', roles },
  { id: 'employees', label: 'Employees', icon: 'UsersRound', roles: ['CEO', 'Manager', 'Lead'] },
  { id: 'profile', label: 'Profile', icon: 'UserRound', roles }
];

function App() {
  const [phase, setPhase] = React.useState('start');
  const [setupStep, setSetupStep] = React.useState(0);
  const [screen, setScreen] = React.useState('overview');
  const [role, setRole] = React.useState('CEO');
  const [toast, setToast] = React.useState('');
  
  // API State
  const [jwt, setJwt] = React.useState(sessionStorage.getItem('jwt_token') || '');
  const [user, setUser] = React.useState(null);
  const [business, setBusiness] = React.useState(null);
  const [tasks, setTasks] = React.useState([]);
  const [employees, setEmployees] = React.useState([]);
  const [availableRoles, setAvailableRoles] = React.useState([]);
  const [availableDepartments, setAvailableDepartments] = React.useState([]);
  const [analytics, setAnalytics] = React.useState(null);
  
  // Login inputs
  const [loginMethod, setLoginMethod] = React.useState('portal');
  const [emailInput, setEmailInput] = React.useState('');
  const [businessNameInput, setBusinessNameInput] = React.useState('');
  const [passwordInput, setPasswordInput] = React.useState('');
  const [phoneNumberInput, setPhoneNumberInput] = React.useState('');
  const [inviteTokenInput, setInviteTokenInput] = React.useState('');
  const [loading, setLoading] = React.useState(false);

  // Auto-dismiss toast after 4s
  React.useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(''), 4000);
    return () => clearTimeout(t);
  }, [toast]);

  // Auto-init Telegram WebApp
  React.useEffect(() => {
    if (window.Telegram && window.Telegram.WebApp) {
      const webapp = window.Telegram.WebApp;
      webapp.ready();
      webapp.expand();
      
      const initData = webapp.initData;
      if (initData) {
        setLoading(true);
        setToast('Connecting to bot secure server...');
        fetch('/api/v1/auth/telegram', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ initData })
        })
        .then(res => {
          if (!res.ok) throw new Error('Auto-auth failed. Please use phone number login.');
          return res.json();
        })
        .then(data => {
          const token = data.accessToken;
          setJwt(token);
          sessionStorage.setItem('jwt_token', token);
          setToast('Authenticated via Telegram.');
        })
        .catch(err => {
          console.warn(err.message);
          setToast(err.message);
        })
        .finally(() => setLoading(false));
      }
    }
  }, []);

  // Fetch data on token update
  React.useEffect(() => {
    if (!jwt) return;
    setLoading(true);
    fetch('/api/v1/auth/me', {
      headers: { 'Authorization': `Bearer ${jwt}` }
    })
    .then(res => {
      if (!res.ok) throw new Error('Session expired');
      return res.json();
    })
    .then(me => {
      return fetch(`/api/v1/businesses/${me.businessId}/users/${me.userId}`, {
        headers: { 'Authorization': `Bearer ${jwt}` }
      })
      .then(res => res.json())
      .then(profile => {
        const userRole = profile.roleNames && profile.roleNames.length > 0 ? profile.roleNames[0] : 'Employee';
        setUser({
          id: profile.id,
          displayName: profile.displayName,
          role: userRole,
          businessId: profile.businessId,
          primaryPhone: profile.primaryPhone,
          email: profile.email
        });
        setRole(userRole);
        setPhase('dashboard');
        loadDashboardData(me.businessId, jwt);
      });
    })
    .catch(err => {
      setToast('Login failed: ' + err.message);
      setJwt('');
      sessionStorage.removeItem('jwt_token');
    })
    .finally(() => setLoading(false));
  }, [jwt]);

  const loadDashboardData = (businessId, token) => {
    const headers = { 'Authorization': `Bearer ${token}` };
    
    fetch(`/api/v1/businesses/${businessId}`, { headers })
      .then(res => res.json()).then(data => setBusiness(data)).catch(console.error);
    fetch(`/api/v1/businesses/${businessId}/tasks`, { headers })
      .then(res => res.json()).then(data => setTasks(data)).catch(console.error);
    fetch(`/api/v1/businesses/${businessId}/users`, { headers })
      .then(res => res.json()).then(data => setEmployees(data)).catch(console.error);
    fetch(`/api/v1/businesses/${businessId}/roles`, { headers })
      .then(res => res.json()).then(data => setAvailableRoles(data)).catch(console.error);
    fetch(`/api/v1/businesses/${businessId}/departments`, { headers })
      .then(res => res.json()).then(data => setAvailableDepartments(data)).catch(console.error);
    fetch(`/api/v1/businesses/${businessId}/analytics`, { headers })
      .then(res => res.json()).then(data => setAnalytics(data)).catch(console.error);
  };

  const handlePortalLogin = (e) => {
    e.preventDefault();
    if (!emailInput.trim() || !businessNameInput.trim() || !passwordInput.trim()) {
      setToast('All fields are required.');
      return;
    }
    setLoading(true);
    fetch('/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: emailInput.trim(), businessName: businessNameInput.trim(), password: passwordInput.trim() })
    })
    .then(res => { if (!res.ok) throw new Error('Invalid credentials. Check email, company name, and password.'); return res.json(); })
    .then(data => { setJwt(data.accessToken); sessionStorage.setItem('jwt_token', data.accessToken); })
    .catch(err => setToast(err.message))
    .finally(() => setLoading(false));
  };

  const handleManualLogin = (e) => {
    e.preventDefault();
    const body = {};
    if (inviteTokenInput.trim()) {
      body.token = inviteTokenInput.trim();
    } else if (phoneNumberInput.trim()) {
      body.phoneNumber = phoneNumberInput.trim();
    } else {
      setToast('Enter phone number or invite token.');
      return;
    }
    setLoading(true);
    fetch('/api/v1/auth/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    })
    .then(res => { if (!res.ok) throw new Error('Invalid credentials.'); return res.json(); })
    .then(data => { setJwt(data.accessToken); sessionStorage.setItem('jwt_token', data.accessToken); })
    .catch(err => setToast(err.message))
    .finally(() => setLoading(false));
  };

  const handleTaskAction = (taskId, action, data = {}) => {
    if (!jwt || !user) return;
    setLoading(true);
    
    let url = `/api/v1/businesses/${user.businessId}/tasks/${taskId}/status`;
    let method = 'PATCH';
    let body = { status: action };

    if (action === 'APPROVE' || action === 'REJECT') {
      url = `/api/v1/businesses/${user.businessId}/tasks/${taskId}/approve`;
      method = 'POST';
      body = {
        approved: action === 'APPROVE',
        reason: data.reason || '',
        assignmentId: data.assignmentId
      };
    }

    fetch(url, {
      method,
      headers: {
        'Authorization': `Bearer ${jwt}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(body)
    })
    .then(res => {
      if (!res.ok) throw new Error('Action failed');
      setToast(`Task marked as ${action}`);
      loadDashboardData(user.businessId, jwt);
    })
    .catch(err => setToast(err.message))
    .finally(() => setLoading(false));
  };

  const handleCreateTask = (taskData) => {
    if (!jwt || !user) return;
    setLoading(true);
    
    fetch(`/api/v1/businesses/${user.businessId}/tasks`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${jwt}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(taskData)
    })
    .then(res => {
      if (!res.ok) throw new Error('Failed to assign task');
      setToast('Task assigned successfully.');
      loadDashboardData(user.businessId, jwt);
    })
    .catch(err => setToast(err.message))
    .finally(() => setLoading(false));
  };

  const handleInviteUser = (inviteData) => {
    if (!jwt || !user) return;
    setLoading(true);

    fetch(`/api/v1/businesses/${user.businessId}/users`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${jwt}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(inviteData)
    })
    .then(res => {
      if (!res.ok) throw new Error('Failed to send invite');
      setToast('Invite sent successfully!');
      loadDashboardData(user.businessId, jwt);
    })
    .catch(err => setToast(err.message))
    .finally(() => setLoading(false));
  };

  const logout = () => {
    setJwt('');
    setUser(null);
    setBusiness(null);
    setTasks([]);
    setEmployees([]);
    setAvailableRoles([]);
    setAvailableDepartments([]);
    setAnalytics(null);
    sessionStorage.removeItem('jwt_token');
    setPhase('start');
  };

  return (
    <main className="telegram-canvas">
      <section className="telegram-app">
        <TelegramHeader
          phase={phase}
          screen={screen}
          loading={loading}
          onBack={() => (phase === 'dashboard' ? logout() : setSetupStep(Math.max(0, setupStep - 1)))}
        />

        {phase === 'start' && (
          <section className="screen start-screen">
            <div className="bot-message">
              <Icon name="Send" />
              <div>
                <p>Welcome to AgentCraft on Telegram.</p>
                <p>Sign in with your portal credentials or phone/invite token.</p>
              </div>
            </div>

            <section className="panel compact">
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '6px', marginBottom: '18px' }}>
                <button
                  type="button"
                  className={loginMethod === 'portal' ? 'primary' : ''}
                  onClick={() => setLoginMethod('portal')}
                  style={{ padding: '8px', borderRadius: '8px', fontSize: '13px' }}
                >
                  Portal Login
                </button>
                <button
                  type="button"
                  className={loginMethod === 'phone' ? 'primary' : ''}
                  onClick={() => setLoginMethod('phone')}
                  style={{ padding: '8px', borderRadius: '8px', fontSize: '13px' }}
                >
                  Phone / Invite
                </button>
              </div>

              {loginMethod === 'portal' ? (
                <form onSubmit={handlePortalLogin} className="action-stack">
                  <label>
                    <span>Email Address *</span>
                    <input type="email" required placeholder="you@company.com" value={emailInput} onChange={e => setEmailInput(e.target.value)} />
                  </label>
                  <label>
                    <span>Company Name *</span>
                    <input type="text" required placeholder="e.g. MINIONS" value={businessNameInput} onChange={e => setBusinessNameInput(e.target.value)} />
                  </label>
                  <label>
                    <span>Password *</span>
                    <input type="password" required placeholder="••••••••" value={passwordInput} onChange={e => setPasswordInput(e.target.value)} />
                  </label>
                  <button type="submit" className="primary" style={{ marginTop: '12px' }} disabled={loading}>
                    {loading ? 'Authenticating...' : 'Login to Workspace'}
                  </button>
                </form>
              ) : (
                <form onSubmit={handleManualLogin} className="action-stack">
                  <label>
                    <span>Phone Number</span>
                    <input type="tel" placeholder="+15550001234" value={phoneNumberInput} onChange={e => setPhoneNumberInput(e.target.value)} />
                  </label>
                  <div style={{ textAlign: 'center', margin: '8px 0', color: 'var(--muted)', fontSize: '12px' }}>— OR —</div>
                  <label>
                    <span>Invite Token</span>
                    <input type="text" placeholder="Enter registration/invite token" value={inviteTokenInput} onChange={e => setInviteTokenInput(e.target.value)} />
                  </label>
                  <button type="submit" className="primary" style={{ marginTop: '12px' }} disabled={loading}>
                    {loading ? 'Signing In...' : 'Verify & Access Portal'}
                  </button>
                </form>
              )}
            </section>
          </section>
        )}

        {phase === 'dashboard' && (
          <Dashboard
            user={user}
            role={role}
            setRole={setRole}
            screen={screen}
            setScreen={setScreen}
            logout={logout}
            tasks={tasks}
            employees={employees}
            business={business}
            availableRoles={availableRoles}
            availableDepartments={availableDepartments}
            analytics={analytics}
            onTaskAction={handleTaskAction}
            onCreateTask={handleCreateTask}
            onInviteUser={handleInviteUser}
          />
        )}
      </section>

      {toast && (
        <button className="toast" onClick={() => setToast('')}>
          <Icon name="CheckCircle2" size={16} />
          <span>{toast}</span>
        </button>
      )}
    </main>
  );
}

function TelegramHeader({ phase, screen, loading, onBack }) {
  const title = phase === 'dashboard' ? navItems.find((item) => item.id === screen)?.label : 'AgentCraft';

  return (
    <header className="tg-header">
      <button className="icon-button" onClick={onBack} aria-label="Back">
        <Icon name="ChevronLeft" />
      </button>
      <div>
        <strong>{title}</strong>
        <span>Telegram Mini App</span>
      </div>
      <button className="icon-button" aria-label="Bot status">
        {loading ? <Icon name="Loader2" size={18} className="spin" /> : <Icon name="Bot" />}
      </button>
    </header>
  );
}

function Dashboard({
  user,
  role,
  setRole,
  screen,
  setScreen,
  logout,
  tasks,
  employees,
  business,
  availableRoles,
  availableDepartments,
  analytics,
  onTaskAction,
  onCreateTask,
  onInviteUser
}) {
  const visibleNav = navItems.filter((item) => item.roles.includes(role));

  return (
    <section className="dashboard">
      <div className="role-row">
        <div>
          <span style={{ fontSize: '13px', color: 'var(--muted)', marginRight: '8px' }}>Active Role:</span>
          <strong>{role}</strong>
        </div>
        <button onClick={logout}><Icon name="LogOut" /> Logout</button>
      </div>

      <div className="content-shell">
        {screen === 'overview' && <OverviewScreen tasks={tasks} business={business} employees={employees} />}
        {screen === 'analytics' && <AnalyticsScreen analytics={analytics} />}
        {screen === 'tasks' && <TasksScreen role={role} tasks={tasks} employees={employees} onTaskAction={onTaskAction} onCreateTask={onCreateTask} />}
        {screen === 'employees' && <DirectoryScreen title="Employees" employees={employees} availableRoles={availableRoles} availableDepartments={availableDepartments} onInviteUser={onInviteUser} />}
        {screen === 'profile' && <ProfileScreen user={user} />}
      </div>

      <nav className="bottom-nav">
        {visibleNav.map((item) => (
          <button className={screen === item.id ? 'active' : ''} key={item.id} onClick={() => setScreen(item.id)}>
            <Icon name={item.icon} size={17} />
            <span>{item.label}</span>
          </button>
        ))}
      </nav>
    </section>
  );
}

function OverviewScreen({ tasks, business, employees }) {
  const openTasks = tasks.filter(t => t.status === 'ASSIGNED' || t.status === 'REJECTED' || t.status === 'IN_PROGRESS').length;
  const pendingApprovals = tasks.filter(t => t.status === 'SUBMITTED').length;

  return (
    <>
      <div className="metric-grid">
        <Metric title="Open Tasks" value={openTasks} desc="Awaiting completion" />
        <Metric title="Pending Approvals" value={pendingApprovals} desc="Awaiting manager review" />
        <Metric title="Total Team Members" value={employees.length} desc="Active staff" />
        <Metric title="Company Name" value={business ? business.name : 'AgentCraft'} desc={business ? business.industry : 'Loading...'} />
      </div>
      <Section title="Business Workspaces">
        <div className="resource-grid" style={{ marginTop: '14px' }}>
          <article>
            <Icon name="BriefcaseBusiness" size={16} />
            <span>Tasks Workspace</span>
          </article>
          <article>
            <Icon name="UsersRound" size={16} />
            <span>Directory Workspace</span>
          </article>
          <article>
            <Icon name="Bot" size={16} />
            <span>Telegram Bot Link</span>
          </article>
          <article>
            <Icon name="ShieldCheck" size={16} />
            <span>Active Security</span>
          </article>
        </div>
      </Section>
    </>
  );
}

function AnalyticsScreen({ analytics }) {
  const openCount = analytics ? analytics.openCount || 0 : 0;
  const completedCount = analytics ? analytics.completedCount || 0 : 0;
  const totalCount = openCount + completedCount;

  return (
    <>
      <Section title="Task Performance Chart">
        <ChartPlaceholder />
      </Section>
      <div className="metric-grid" style={{ marginTop: '14px' }}>
        <Metric title="Completed Tasks" value={completedCount} desc="Completed successfully" />
        <Metric title="Open Tasks" value={openCount} desc="Still in progress" />
        <Metric title="Total Tracked Tasks" value={totalCount} desc="All time" />
      </div>
    </>
  );
}

function TasksScreen({ role, tasks, employees, onTaskAction, onCreateTask }) {
  const [showForm, setShowForm] = React.useState(false);
  const [title, setTitle] = React.useState('');
  const [description, setDescription] = React.useState('');
  const [dueDate, setDueDate] = React.useState('');
  const [priority, setPriority] = React.useState('MEDIUM');
  const [assigneeId, setAssigneeId] = React.useState('');

  const submitTask = (e) => {
    e.preventDefault();
    if (!title) return;
    onCreateTask({
      title,
      description,
      dueDate: dueDate ? dueDate + "T23:59:59Z" : null,
      priority,
      assigneeId: assigneeId || null
    });
    setShowForm(false);
    setTitle('');
    setDescription('');
    setDueDate('');
    setPriority('MEDIUM');
    setAssigneeId('');
  };

  return (
    <Section
      title="Tasks"
      action={role !== 'Employee' ? (showForm ? 'Cancel' : 'Create Task') : ''}
      onActionClick={() => setShowForm(!showForm)}
    >
      {showForm && (
        <form onSubmit={submitTask} className="panel" style={{ marginTop: '14px', display: 'grid', gap: '10px' }}>
          <label>
            <span>Task Title *</span>
            <input type="text" required value={title} onChange={e => setTitle(e.target.value)} />
          </label>
          <label>
            <span>Description</span>
            <textarea value={description} onChange={e => setDescription(e.target.value)} />
          </label>
          <label>
            <span>Due Date</span>
            <input type="date" value={dueDate} onChange={e => setDueDate(e.target.value)} />
          </label>
          <label>
            <span>Priority</span>
            <select value={priority} onChange={e => setPriority(e.target.value)}>
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
              <option value="CRITICAL">Critical</option>
            </select>
          </label>
          <label>
            <span>Assignee</span>
            <select value={assigneeId} onChange={e => setAssigneeId(e.target.value)}>
              <option value="">Unassigned</option>
              {employees.map(emp => (
                <option key={emp.id} value={emp.id}>{emp.displayName} ({emp.primaryPhone || 'No Phone'})</option>
              ))}
            </select>
          </label>
          <button type="submit" className="primary" style={{ marginTop: '10px' }}>Assign Now</button>
        </form>
      )}

      <div style={{ marginTop: '14px' }}>
        <DataTable
          columns={['Task Title', 'Priority', 'Due Date', 'Status', 'Actions']}
          data={tasks}
          renderRow={(task) => (
            <tr key={task.id}>
              <td>
                <strong>{task.title}</strong>
                <p className="muted" style={{ margin: '3px 0 0', fontSize: '12px' }}>{task.description || 'No description'}</p>
              </td>
              <td><span className={`badge ${task.priority.toLowerCase()}`}>{task.priority}</span></td>
              <td>{task.dueDate ? task.dueDate.substring(0, 10) : '—'}</td>
              <td><span className={`badge ${task.status.toLowerCase()}`}>{task.status}</span></td>
              <td>
                {role === 'Employee' && (task.status === 'ASSIGNED' || task.status === 'REJECTED') && (
                  <button onClick={() => onTaskAction(task.id, 'SUBMITTED')} style={{ borderColor: 'var(--success)', color: 'var(--success)' }}>
                    Complete
                  </button>
                )}
                {role !== 'Employee' && task.status === 'SUBMITTED' && (
                  <div style={{ display: 'flex', gap: '5px' }}>
                    <button onClick={() => onTaskAction(task.id, 'APPROVE')} style={{ background: 'var(--success)', color: '#fff', border: 0 }}>
                      Approve
                    </button>
                    <button onClick={() => onTaskAction(task.id, 'REJECT')} style={{ background: 'var(--error)', color: '#fff', border: 0 }}>
                      Reject
                    </button>
                  </div>
                )}
              </td>
            </tr>
          )}
        />
      </div>
    </Section>
  );
}

function DirectoryScreen({ title, employees, availableRoles, availableDepartments, onInviteUser }) {
  const [showInvite, setShowInvite] = React.useState(false);
  const [phone, setPhone] = React.useState('');
  const [displayName, setDisplayName] = React.useState('');
  const [roleId, setRoleId] = React.useState('');
  const [departmentId, setDepartmentId] = React.useState('');

  const submitInvite = (e) => {
    e.preventDefault();
    if (!phone || !roleId) return;
    onInviteUser({
      phoneNumber: phone,
      displayName: displayName || null,
      roleId,
      departmentId: departmentId || null
    });
    setShowInvite(false);
    setPhone('');
    setDisplayName('');
    setRoleId('');
    setDepartmentId('');
  };

  return (
    <Section
      title={title}
      action={showInvite ? 'Cancel' : 'Invite User'}
      onActionClick={() => {
        setShowInvite(!showInvite);
        if (availableRoles.length > 0 && !roleId) setRoleId(availableRoles[0].id);
      }}
    >
      {showInvite && (
        <form onSubmit={submitInvite} className="panel" style={{ marginTop: '14px', display: 'grid', gap: '10px' }}>
          <label>
            <span>Phone Number *</span>
            <input type="tel" required placeholder="+15550001234" value={phone} onChange={e => setPhone(e.target.value)} />
          </label>
          <label>
            <span>Display Name</span>
            <input type="text" placeholder="Full name (optional)" value={displayName} onChange={e => setDisplayName(e.target.value)} />
          </label>
          <label>
            <span>Role *</span>
            <select required value={roleId} onChange={e => setRoleId(e.target.value)}>
              <option value="">— Select a role —</option>
              {availableRoles.map(r => (
                <option key={r.id} value={r.id}>{r.name}</option>
              ))}
            </select>
          </label>
          <label>
            <span>Department</span>
            <select value={departmentId} onChange={e => setDepartmentId(e.target.value)}>
              <option value="">— No department —</option>
              {availableDepartments.map(d => (
                <option key={d.id} value={d.id}>{d.name}</option>
              ))}
            </select>
          </label>
          <button type="submit" className="primary" style={{ marginTop: '10px' }}>Send Invitation Link</button>
        </form>
      )}

      <div style={{ marginTop: '14px' }}>
        <DataTable
          columns={['Full Name', 'Primary Phone', 'Email Address', 'Roles', 'Status']}
          data={employees}
          renderRow={(emp) => (
            <tr key={emp.id}>
              <td><strong>{emp.displayName}</strong></td>
              <td>{emp.primaryPhone || '—'}</td>
              <td>{emp.email || '—'}</td>
              <td>
                {(emp.roleNames || []).map(r => (
                  <span key={r} className="badge" style={{ marginRight: '4px' }}>{r}</span>
                ))}
              </td>
              <td><span className={`badge ${emp.status.toLowerCase()}`}>{emp.status}</span></td>
            </tr>
          )}
        />
      </div>
    </Section>
  );
}

function ProfileScreen({ user }) {
  return (
    <Section title="Profile">
      <div className="profile-card" style={{ marginTop: '14px' }}>
        <div className="avatar"><Icon name="UserRound" size={26} /></div>
        <div>
          <h2>{user ? user.displayName : 'Loading...'}</h2>
          <p>{user ? user.email : 'No email address registered'}</p>
          <div className="badge-row">
            <span className="badge primary">{user ? user.role : 'Employee'}</span>
            <span className="badge">{user ? user.primaryPhone : 'No Phone'}</span>
            <span className="badge success">Telegram Verified</span>
          </div>
        </div>
      </div>
    </Section>
  );
}

function Section({ title, action, onActionClick, children }) {
  return (
    <section className="panel section-panel">
      <header className="section-header">
        <h1>{title}</h1>
        {action && <button type="button" onClick={onActionClick}>{action}</button>}
      </header>
      {children}
    </section>
  );
}

function DataTable({ columns, data, renderRow }) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>{columns.map((column) => <th key={column}>{column}</th>)}</tr>
        </thead>
        <tbody>
          {data && data.length > 0 ? (
            data.map(renderRow)
          ) : (
            <tr>
              <td colSpan={columns.length}>
                <div className="empty-inline">
                  <span>No records found.</span>
                </div>
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

function Metric({ title, value, desc }) {
  return (
    <article className="metric-card">
      <span>{title}</span>
      <strong>{value}</strong>
      <p>{desc}</p>
    </article>
  );
}

function ChartPlaceholder() {
  return (
    <div className="chart-placeholder">
      {[38, 62, 46, 74, 52, 86].map((height, index) => <span style={{ height: `${height}%` }} key={index} />)}
    </div>
  );
}

createRoot(document.getElementById('root')).render(<App />);
