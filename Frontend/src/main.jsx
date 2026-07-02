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
  UsersRound
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
  UsersRound
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
  { id: 'conversations', label: 'Conversations', icon: 'MessageSquare', roles },
  { id: 'knowledge', label: 'Knowledge Base', icon: 'BookOpen', roles },
  { id: 'tasks', label: 'Tasks', icon: 'ClipboardCheck', roles },
  { id: 'employees', label: 'Employees', icon: 'UsersRound', roles: ['CEO', 'Manager', 'Lead'] },
  { id: 'managers', label: 'Managers', icon: 'ShieldCheck', roles: ['CEO'] },
  { id: 'notifications', label: 'Notifications', icon: 'Bell', roles },
  { id: 'settings', label: 'Settings', icon: 'Settings', roles: ['CEO', 'Manager'] },
  { id: 'profile', label: 'Profile', icon: 'UserRound', roles },
  { id: 'telegram', label: 'Telegram', icon: 'Bot', roles: ['CEO', 'Manager'] }
];

const validationStates = [
  'Validate Telegram username',
  'Validate Bot Token',
  'Validate duplicate business',
  'Validate role permissions',
  'Validate webhook',
  'Validate session'
];

const errorStates = [
  'Invalid Bot Token',
  'Webhook Failure',
  'Duplicate Business',
  'Portal Not Found',
  'Permission Denied',
  'Session Expired',
  'Network Failure'
];

const loadingMessages = [
  'Please wait while we verify your Telegram Bot.',
  'Please wait while we configure your webhook.',
  'Please wait while we create your secure portal.',
  'Please wait while we transfer you to your portal.'
];

function App() {
  const [phase, setPhase] = React.useState('start');
  const [setupStep, setSetupStep] = React.useState(0);
  const [screen, setScreen] = React.useState('overview');
  const [role, setRole] = React.useState('CEO');
  const [toast, setToast] = React.useState('Telegram Mini App ready.');

  const startCreate = () => {
    setPhase('setup');
    setSetupStep(0);
    setToast('Choose a portal workflow.');
  };

  const accessPortal = () => {
    setPhase('dashboard');
    setScreen('overview');
    setToast('Loading dashboard.');
  };

  return (
    <main className="telegram-canvas">
      <section className="telegram-app">
        <TelegramHeader
          phase={phase}
          screen={screen}
          onBack={() => (phase === 'dashboard' ? setPhase('start') : setSetupStep(Math.max(0, setupStep - 1)))}
        />

        {phase === 'start' && <StartScreen onCreate={startCreate} onAccess={accessPortal} />}
        {phase === 'setup' && (
          <SetupFlow
            step={setupStep}
            setStep={setSetupStep}
            enterPortal={accessPortal}
          />
        )}
        {phase === 'dashboard' && (
          <Dashboard
            role={role}
            setRole={setRole}
            screen={screen}
            setScreen={setScreen}
            logout={() => setPhase('start')}
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

function TelegramHeader({ phase, screen, onBack }) {
  const title = phase === 'dashboard' ? navItems.find((item) => item.id === screen)?.label : 'BizPortal';

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
        <Icon name="Bot" />
      </button>
    </header>
  );
}

function StartScreen({ onCreate, onAccess }) {
  return (
    <section className="screen start-screen">
      <div className="bot-message">
        <Icon name="Send" />
        <div>
          <p>Welcome to BizPortal on Telegram.</p>
          <p>Use /start to begin.</p>
        </div>
      </div>

      <section className="panel">
        <p className="eyebrow">/start</p>
        <h1>Create or access your portal</h1>
        <p className="muted">This screen is the Telegram entry point, not a separate website page.</p>
        <div className="action-stack">
          <button className="primary" onClick={onCreate}>
            Create New Portal <Icon name="ChevronRight" />
          </button>
          <button onClick={onAccess}>
            Access Existing Portal <Icon name="Plug" />
          </button>
        </div>
      </section>

      <section className="panel compact">
        <h2>Public states inside Mini App</h2>
        <Segmented items={['Landing', 'Login', 'Register']} />
        <StateBadges items={['Validate session', 'Portal Not Found', 'Session Expired']} />
      </section>
    </section>
  );
}

function SetupFlow({ step, setStep, enterPortal }) {
  const [selectedWorkflow, setSelectedWorkflow] = React.useState(null);
  const [botToken, setBotToken] = React.useState('');
  const [verificationStatus, setVerificationStatus] = React.useState('idle');
  const isLast = step === setupSteps.length - 1;

  React.useEffect(() => {
    if (step !== 5 || verificationStatus !== 'loading') return undefined;

    const timer = window.setTimeout(() => {
      setVerificationStatus(botToken.trim() ? 'success' : 'error');
    }, 1100);

    return () => window.clearTimeout(timer);
  }, [botToken, step, verificationStatus]);

  const next = () => {
    if (step === 0 && !selectedWorkflow) return;
    if (step === 0 && selectedWorkflow === 'access') {
      setStep(3);
      return;
    }
    if (step === 4) {
      setVerificationStatus('loading');
      setStep(5);
      return;
    }
    if (step === 5 && verificationStatus !== 'success') return;
    isLast ? enterPortal() : setStep(step + 1);
  };

  return (
    <form className="screen setup-screen" onSubmit={(e) => { e.preventDefault(); next(); }}>
      <Stepper steps={setupSteps} active={step} />

      {step === 0 && (
        <FormPanel
          eyebrow="Portal"
          title="Create or Access Portal"
          description="Would you like to Create a New Portal or Access Existing Portal?"
          fields={[]}
        >
          <div className="choice-grid">
            <WorkflowChoice
              icon="BriefcaseBusiness"
              title="Create New Portal"
              text="Start CEO setup for identity, business information, workflow, bot token and webhook."
              selected={selectedWorkflow === 'create'}
              onClick={() => setSelectedWorkflow('create')}
            />
            <WorkflowChoice
              icon="Plug"
              title="Access Existing Portal"
              text="Validate portal, Telegram account, role permission and session."
              selected={selectedWorkflow === 'access'}
              onClick={() => setSelectedWorkflow('access')}
            />
          </div>
        </FormPanel>
      )}

      {step === 1 && (
        <FormPanel
          eyebrow="Identity"
          title="Identity"
          description="Please enter your full name."
          fields={[
            { label: 'Full name', placeholder: 'User.fullName' },
            { label: 'Role', type: 'select', options: roles }
          ]}
        />
      )}

      {step === 2 && (
        <FormPanel
          eyebrow="Business"
          title="Business Information"
          description="Fields map to Business: company name, type, description and workflow."
          fields={[
            { label: 'Company name', placeholder: 'Business.name' },
            { label: 'Business type', type: 'select', options: ['Retail', 'Service', 'Manufacturing', 'Other'] },
            { label: 'Company description', placeholder: 'Business.description', textarea: true },
            { label: 'Workflow', placeholder: 'Business.workflow or templateId', textarea: true }
          ]}
        >
          <StateBadges items={['Validate duplicate business', 'Syncing company information.']} />
        </FormPanel>
      )}

      {step === 3 && (
        <FormPanel
          eyebrow="Telegram"
          title="Telegram Account"
          description="Please confirm your Telegram username."
          fields={[
            { label: 'Telegram username', placeholder: '@username' },
            { label: 'Telegram chat id', placeholder: 'Resolved from initData', disabled: true }
          ]}
        >
          <StateBadges items={['Validate Telegram username', 'Validate session']} />
        </FormPanel>
      )}

      {step === 4 && (
        <FormPanel
          eyebrow="Bot"
          title="Bot Configuration"
          description="Have you created your bot using @BotFather? Paste Bot Token."
          fields={[
            { label: 'Bot created using @BotFather?', type: 'select', options: ['Yes', 'No'] },
            { label: 'Telegram Bot Token', placeholder: 'Bot.token', value: botToken, onChange: setBotToken },
            { label: 'Bot username', placeholder: 'Bot.username' },
            { label: 'Webhook secret', placeholder: 'Generated server-side', disabled: true }
          ]}
        >
          <StateBadges items={['Validate Bot Token', 'Validate webhook']} />
        </FormPanel>
      )}

      {step === 5 && (
        <VerificationPanel
          status={verificationStatus}
          onRetry={() => setVerificationStatus('loading')}
        />
      )}

      {step === 6 && (
        <section className="panel">
          <p className="eyebrow">Portal Creation</p>
          <h1>Create secure portal</h1>
          <ProgressList />
        </section>
      )}

      <footer className="step-actions">
        <button type="button" disabled={step === 0} onClick={() => setStep(step - 1)}>
          <Icon name="ChevronLeft" /> Back
        </button>
        <button type="submit" className="primary" disabled={(step === 0 && !selectedWorkflow) || (step === 5 && verificationStatus !== 'success')}>
          {isLast ? 'Open Dashboard' : 'Continue'} <Icon name="ChevronRight" />
        </button>
      </footer>
    </form>
  );
}

function Dashboard({ role, setRole, screen, setScreen, logout }) {
  const visibleNav = navItems.filter((item) => item.roles.includes(role));

  return (
    <section className="dashboard">
      <div className="role-row">
        <select value={role} onChange={(event) => setRole(event.target.value)}>
          {roles.map((item) => <option key={item}>{item}</option>)}
        </select>
        <button onClick={logout}><Icon name="LogOut" /> Logout</button>
      </div>

      <div className="content-shell">
        {screen === 'overview' && <OverviewScreen />}
        {screen === 'analytics' && <AnalyticsScreen />}
        {screen === 'conversations' && <ConversationsScreen />}
        {screen === 'knowledge' && <KnowledgeScreen />}
        {screen === 'tasks' && <TasksScreen role={role} />}
        {screen === 'employees' && <DirectoryScreen title="Employees" entity="Employee" />}
        {screen === 'managers' && <DirectoryScreen title="Managers" entity="Role" />}
        {screen === 'notifications' && <NotificationsScreen />}
        {screen === 'settings' && <SettingsScreen />}
        {screen === 'profile' && <ProfileScreen role={role} />}
        {screen === 'telegram' && <TelegramScreen />}
      </div>

      <nav className="bottom-nav">
        {visibleNav.slice(0, 5).map((item) => (
          <button className={screen === item.id ? 'active' : ''} key={item.id} onClick={() => setScreen(item.id)}>
            <Icon name={item.icon} size={17} />
            <span>{item.label}</span>
          </button>
        ))}
      </nav>

      <div className="more-nav">
        {visibleNav.slice(5).map((item) => (
          <button className={screen === item.id ? 'active' : ''} key={item.id} onClick={() => setScreen(item.id)}>
            <Icon name={item.icon} size={16} />
            {item.label}
          </button>
        ))}
      </div>
    </section>
  );
}

function OverviewScreen() {
  return (
    <>
      <div className="metric-grid">
        {[
          ['Open tasks', '0', 'Task API pending'],
          ['Pending approvals', '0', 'Approval API pending'],
          ['Conversations', '0', 'Telegram sync pending'],
          ['Webhook', 'Not verified', 'Bot setup required']
        ].map((item) => <Metric item={item} key={item[0]} />)}
      </div>
      <Section title="Dashboard resources" action="Refresh">
        <ResourceList items={['Business', 'User', 'Telegram Account', 'Bot', 'Task', 'Department', 'Employee', 'Role']} />
      </Section>
      <Section title="Loading and retry states" action="Inspect">
        <StateBadges items={[...validationStates, ...errorStates]} />
      </Section>
    </>
  );
}

function AnalyticsScreen() {
  return (
    <>
      <Section title="Analytics" action="Filter">
        <ChartPlaceholder />
      </Section>
      <EmptyState title="No analytics records loaded" text="Charts are waiting for dashboard and conversation APIs." />
    </>
  );
}

function ConversationsScreen() {
  return (
    <Section title="Conversations" action="New message">
      <Toolbar />
      <DataTable columns={['Conversation ID', 'Participant', 'Status', 'Last message', 'Updated']} entity="Conversation" />
      <Composer placeholder="Enter message" />
    </Section>
  );
}

function KnowledgeScreen() {
  return (
    <Section title="Knowledge Base" action="Create article">
      <Toolbar />
      <DataTable columns={['Article ID', 'Title', 'Visibility', 'Owner role', 'Status']} entity="KnowledgeBaseArticle" />
    </Section>
  );
}

function TasksScreen({ role }) {
  return (
    <Section title="Tasks" action={role === 'Employee' ? 'Upload proof' : 'Assign task'}>
      <Toolbar />
      <DataTable columns={['Task ID', 'Title', 'Owner', 'Priority', 'Deadline', 'Status']} entity="Task" />
      <StateBadges items={['Enter task title', 'Choose priority', 'Enter deadline', 'Upload proof', 'Approve or Reject']} />
    </Section>
  );
}

function DirectoryScreen({ title, entity }) {
  return (
    <Section title={title} action="Invite">
      <Toolbar />
      <DataTable columns={['User ID', 'Full name', 'Telegram username', 'Role', 'Department', 'Status']} entity={entity} />
    </Section>
  );
}

function NotificationsScreen() {
  return (
    <Section title="Notifications" action="Mark read">
      <DataTable columns={['Notification ID', 'Type', 'Message', 'Status', 'Created']} entity="Notification" />
    </Section>
  );
}

function SettingsScreen() {
  return (
    <>
      <FormPanel
        eyebrow="Settings"
        title="Business Settings"
        description="Editable only after session, role and permission checks pass."
        fields={[
          { label: 'Business name', placeholder: 'Business.name' },
          { label: 'Business type', type: 'select', options: ['Retail', 'Service', 'Manufacturing', 'Other'] },
          { label: 'Description', placeholder: 'Business.description', textarea: true }
        ]}
      />
      <Section title="Role Permissions" action="Audit logs">
        <DataTable columns={['Role', 'Permission', 'Scope', 'Status']} entity="RolePermission" />
      </Section>
    </>
  );
}

function ProfileScreen({ role }) {
  return (
    <Section title="Profile" action="Edit">
      <div className="profile-card">
        <div className="avatar"><Icon name="UserRound" size={26} /></div>
        <div>
          <h2>User profile</h2>
          <p>User.fullName, TelegramAccount.username and role are loaded from authenticated Telegram session.</p>
          <StateBadges items={[role, 'Validate initData', 'Validate session']} />
        </div>
      </div>
    </Section>
  );
}

function TelegramScreen() {
  return (
    <>
      <FormPanel
        eyebrow="Telegram"
        title="Bot Configuration"
        description="Bot token, webhook and Mini App status map directly to Bot and Telegram Account resources."
        fields={[
          { label: 'Bot username', placeholder: 'Bot.username' },
          { label: 'Bot token', placeholder: 'Bot.token' },
          { label: 'Webhook status', placeholder: 'Bot.webhookStatus', disabled: true },
          { label: 'Mini App status', placeholder: 'Bot.miniAppStatus', disabled: true }
        ]}
      />
      <Section title="Integration Status" action="Retry">
        <ProgressList />
      </Section>
    </>
  );
}

function Stepper({ steps, active }) {
  return (
    <div className="stepper">
      {steps.map((step, index) => (
        <span className={index === active ? 'active' : index < active ? 'done' : ''} key={step}>
          {index < active ? <Icon name="Check" size={13} /> : index + 1}
        </span>
      ))}
    </div>
  );
}

function FormPanel({ eyebrow, title, description, fields, children }) {
  return (
    <section className="panel">
      <p className="eyebrow">{eyebrow}</p>
      <h1>{title}</h1>
      <p className="muted">{description}</p>
      {fields.length > 0 && (
        <div className="form-grid">
          {fields.map((field) => <Field field={field} key={field.label} />)}
        </div>
      )}
      {children}
    </section>
  );
}

function Field({ field }) {
  return (
    <label className={field.textarea ? 'wide' : ''}>
      <span>{field.label}</span>
      {field.type === 'select' ? (
        <select disabled={field.disabled} required>
          {field.options.map((option) => <option key={option}>{option}</option>)}
        </select>
      ) : field.textarea ? (
        <textarea placeholder={field.placeholder} disabled={field.disabled} required />
      ) : (
        <input
          placeholder={field.placeholder}
          disabled={field.disabled}
          value={field.value ?? undefined}
          onChange={field.onChange ? (event) => field.onChange(event.target.value) : undefined}
          required
        />
      )}
    </label>
  );
}

function WorkflowChoice({ icon, title, text, selected, onClick }) {
  return (
    <button className={`choice-card ${selected ? 'selected' : ''}`} onClick={onClick} type="button">
      <Icon name={icon} size={22} />
      <strong>{title}</strong>
      <span>{text}</span>
    </button>
  );
}

function AsyncState({ icon, title, text, action }) {
  return (
    <article className="state-card">
      <Icon name={icon} size={22} />
      <strong>{title}</strong>
      <span>{text}</span>
      {action && <button>{action}</button>}
    </article>
  );
}

function VerificationPanel({ status, onRetry }) {
  const content = {
    loading: {
      icon: 'Loader2',
      title: 'Verifying Telegram Bot',
      text: 'Please wait while we verify your Telegram Bot.'
    },
    success: {
      icon: 'CheckCircle2',
      title: 'Bot verified',
      text: 'Webhook configured successfully.'
    },
    error: {
      icon: 'AlertCircle',
      title: 'Invalid Bot Token',
      text: 'The bot token could not be verified. Paste a valid token and retry.'
    },
    idle: {
      icon: 'Bot',
      title: 'Ready to verify',
      text: 'Verification will start when this step opens.'
    }
  }[status];

  return (
    <section className="panel verification-panel">
      <p className="eyebrow">Bot Verification</p>
      <article className={`verification-card ${status}`}>
        <Icon name={content.icon} size={28} />
        <div>
          <h1>{content.title}</h1>
          <p className="muted">{content.text}</p>
        </div>
        {status === 'error' && <button type="button" onClick={onRetry}>Retry</button>}
      </article>
    </section>
  );
}

function ProgressList() {
  return (
    <div className="progress-list">
      {loadingMessages.map((message, index) => (
        <div key={message}>
          <span>{index === 0 ? <Icon name="Loader2" size={14} /> : index + 1}</span>
          <p>{message}</p>
        </div>
      ))}
    </div>
  );
}

function Section({ title, action, children }) {
  return (
    <section className="panel section-panel">
      <header className="section-header">
        <h1>{title}</h1>
        <button>{action}</button>
      </header>
      {children}
    </section>
  );
}

function Toolbar() {
  return (
    <div className="toolbar">
      <label>
        <Icon name="Search" size={15} />
        <input placeholder="Search API records" />
      </label>
      <button><Icon name="Filter" /> Filter</button>
    </div>
  );
}

function DataTable({ columns, entity }) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>{columns.map((column) => <th key={column}>{column}</th>)}</tr>
        </thead>
        <tbody>
          <tr>
            <td colSpan={columns.length}>
              <EmptyInline entity={entity} />
            </td>
          </tr>
        </tbody>
      </table>
      <footer className="table-footer">
        <span>Pagination ready: page, limit, total</span>
        <div>
          <button disabled>Previous</button>
          <button disabled>Next</button>
        </div>
      </footer>
    </div>
  );
}

function EmptyInline({ entity }) {
  return (
    <div className="empty-inline">
      <Icon name="Loader2" size={18} />
      <span>No {entity} records loaded. Waiting for API response.</span>
    </div>
  );
}

function EmptyState({ title, text }) {
  return (
    <section className="panel empty-state">
      <Icon name="BookOpen" size={28} />
      <h1>{title}</h1>
      <p className="muted">{text}</p>
    </section>
  );
}

function Metric({ item }) {
  return (
    <article className="metric-card">
      <span>{item[0]}</span>
      <strong>{item[1]}</strong>
      <p>{item[2]}</p>
    </article>
  );
}

function ResourceList({ items }) {
  return (
    <div className="resource-grid">
      {items.map((item) => (
        <article key={item}>
          <Icon name="BriefcaseBusiness" size={16} />
          <span>{item}</span>
        </article>
      ))}
    </div>
  );
}

function StateBadges({ items }) {
  return (
    <div className="badge-row">
      {items.map((item) => <Badge key={item} label={item} />)}
    </div>
  );
}

function Badge({ label }) {
  const tone = label.toLowerCase().replaceAll(' ', '-');
  return <span className={`badge ${tone}`}>{label}</span>;
}

function Segmented({ items }) {
  return (
    <div className="segmented">
      {items.map((item, index) => <button className={index === 0 ? 'active' : ''} key={item}>{item}</button>)}
    </div>
  );
}

function Composer({ placeholder }) {
  return (
    <div className="composer">
      <input placeholder={placeholder} />
      <button className="primary"><Icon name="Send" /> Send</button>
    </div>
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
