# Phase 2: Frontend Portal Component Wiring — Detailed Implementation Guide

**Objective**: Wire all React components in `Frontend/src/main.jsx` to live Spring Boot REST API endpoints.

---

## 📋 File to Modify
- **Main file**: `Frontend/src/main.jsx`

---

## 🔍 Current App State Architecture

### State Variables Already Defined
```javascript
// Auth & User
const [phase, setPhase]           = React.useState('start');     // 'start', 'login', 'portal'
const [screen, setScreen]         = React.useState('overview');  // Active tab
const [role, setRole]             = React.useState('CEO');       // User's role
const [jwt, setJwt]               = React.useState(...);         // Bearer token
const [user, setUser]             = React.useState(null);        // Current user object
const [business, setBusiness]     = React.useState(null);        // Business object

// Data
const [tasks, setTasks]                   = React.useState([]);
const [employees, setEmployees]           = React.useState([]);
const [departments, setDepartments]       = React.useState([]);
const [availableRoles, setAvailableRoles] = React.useState([]);
const [analytics, setAnalytics]           = React.useState(null);
const [telegramStatus, setTelegramStatus] = React.useState(null);

// Form inputs
const [loginMethod, setLoginMethod]       = React.useState('portal');
const [emailInput, setEmailInput]         = React.useState('');
const [businessNameInput, setBusinessNameInput] = React.useState('');
```

---

## 🎯 Phase 2 Implementation Tasks

### TASK 2.1: Fix & Wire Overview Dashboard Tab

**Location**: `Frontend/src/main.jsx` → Overview Dashboard Render Block

**Endpoints Required**:
```
GET /api/v1/analytics/summary
GET /api/v1/tasks/recent
GET /api/v1/users/count
```

**Components to Update**:

1. **Load Data on Tab Switch**
   - When `screen === 'overview'`, call both endpoints
   - Store results in `[analytics, setAnalytics]` and `[tasks, setTasks]`

2. **Stat Cards Section**
   - Card 1: "Tasks"
     - Display: `tasks.length` or from analytics summary
     - Icon: `<Icon name="ClipboardCheck" />`
   
   - Card 2: "Employees"
     - Display: `employees.length` or from analytics summary
     - Icon: `<Icon name="UsersRound" />`
   
   - Card 3: "Departments"
     - Display: `departments.length`
     - Icon: `<Icon name="Building2" />`
   
   - Card 4: "Completion Rate"
     - Display: `analytics.completionRate` (percentage format)
     - Icon: `<Icon name="TrendingUp" />`

3. **Recent Activity Section**
   - Display last 5 tasks from `tasks` array
   - Show: task title, assignee, status, due date
   - Format: `_"Task Title"_ — Assigned to Rahul (Due: 2026-07-24)`

**Implementation Code Pattern**:
```javascript
React.useEffect(() => {
  if (screen === 'overview' && jwt) {
    // Fetch analytics summary
    apiFetch('http://localhost:8080/api/v1/analytics/summary', jwt)
      .then(data => setAnalytics(data))
      .catch(err => console.error('Analytics fetch failed:', err));
    
    // Fetch recent tasks
    apiFetch('http://localhost:8080/api/v1/tasks/recent', jwt)
      .then(data => setTasks(data))
      .catch(err => console.error('Tasks fetch failed:', err));
  }
}, [screen, jwt]);
```

---

### TASK 2.2: Wire Analytics View Tab

**Location**: `Frontend/src/main.jsx` → Analytics Tab Render Block

**Endpoints Required**:
```
GET /api/v1/analytics
GET /api/v1/analytics/export?format=pdf&dateRange=monthly
```

**Components to Update**:

1. **Date Range Filter**
   - Add state: `const [dateRange, setDateRange] = React.useState('monthly');`
   - Options: daily, weekly, monthly, quarterly, yearly
   - On change: refetch analytics data

2. **Performance Metrics Display**
   - Fetch from `GET /api/v1/analytics`
   - Expected response fields:
     ```json
     {
       "completionRate": 87.5,
       "averageTaskTime": 3.2,
       "overdueCount": 5,
       "overallScore": 92,
       "departmentMetrics": [
         {"department": "Engineering", "score": 95, "completionRate": 90},
         {"department": "Sales", "score": 88, "completionRate": 85}
       ]
     }
     ```
   - Display in cards/bars

3. **Export Report Button**
   - Add event handler: `onClick={() => handleExportReport()}`
   - Function implementation:
     ```javascript
     const handleExportReport = async () => {
       try {
         const response = await fetch(
           'http://localhost:8080/api/v1/analytics/export?format=pdf&dateRange=' + dateRange,
           { headers: { Authorization: `Bearer ${jwt}` } }
         );
         const blob = await response.blob();
         const url = window.URL.createObjectURL(blob);
         const a = document.createElement('a');
         a.href = url;
         a.download = `report_${dateRange}_${new Date().toISOString().split('T')[0]}.pdf`;
         a.click();
         window.URL.revokeObjectURL(url);
       } catch (err) {
         setToast({ msg: 'Export failed: ' + err.message, type: 'error' });
       }
     };
     ```

4. **Charts/Visualizations**
   - Display performance trends
   - Show department comparison

---

### TASK 2.3: Wire Task Management Tab

**Location**: `Frontend/src/main.jsx` → Tasks Tab Render Block

**Endpoints Required**:
```
GET /api/v1/tasks                              (list all)
POST /api/v1/tasks                             (create new)
PUT /api/v1/tasks/{taskId}/status              (update status)
POST /api/v1/tasks/{taskId}/approve            (approve/reject)
GET /api/v1/tasks/{taskId}/history             (view history)
```

**Components to Update**:

1. **Load Tasks on Tab Switch**
   ```javascript
   React.useEffect(() => {
     if (screen === 'tasks' && jwt) {
       apiFetch('http://localhost:8080/api/v1/tasks', jwt)
         .then(data => setTasks(Array.isArray(data) ? data : data.data || []))
         .catch(err => {
           setToast({ msg: 'Failed to load tasks', type: 'error' });
           console.error(err);
         });
     }
   }, [screen, jwt]);
   ```

2. **Task Status Filter**
   - Add state: `const [taskFilter, setTaskFilter] = React.useState('all');`
   - Options: all, ASSIGNED, IN_PROGRESS, SUBMITTED, COMPLETED
   - Filter displayed tasks based on selection
   - Refetch on filter change

3. **Create Task Modal**
   - Trigger: Click "Create Task" button
   - Add modal state: `const [showCreateTaskModal, setShowCreateTaskModal] = React.useState(false);`
   - Form fields:
     ```javascript
     const [newTask, setNewTask] = React.useState({
       title: '',
       description: '',
       assigneeId: '',
       priority: 'MEDIUM',
       dueDate: ''
     });
     ```
   - On submit:
     ```javascript
     const handleCreateTask = async () => {
       try {
         const response = await apiFetch(
           'http://localhost:8080/api/v1/tasks',
           jwt,
           {
             method: 'POST',
             body: JSON.stringify({
               ...newTask,
               businessId: business.id
             })
           }
         );
         setTasks([...tasks, response]);
         setShowCreateTaskModal(false);
         setToast({ msg: '✅ Task created successfully', type: 'success' });
         setNewTask({ title: '', description: '', assigneeId: '', priority: 'MEDIUM', dueDate: '' });
       } catch (err) {
         setToast({ msg: '❌ Failed to create task: ' + err.message, type: 'error' });
       }
     };
     ```

4. **Task List Display**
   - Render each task with:
     - Title (clickable → show history)
     - Assignee name
     - Status badge (color-coded)
     - Priority icon
     - Due date
     - Action buttons: [Edit] [Delete] [Approve] [Reject]
   - Sorting: by due date, priority, status

5. **Task Status Update**
   - Dropdown on each task row
   - Options: ASSIGNED → IN_PROGRESS → SUBMITTED → COMPLETED
   - On change:
     ```javascript
     const handleUpdateTaskStatus = async (taskId, newStatus) => {
       try {
         await apiFetch(
           `http://localhost:8080/api/v1/tasks/${taskId}/status`,
           jwt,
           {
             method: 'PUT',
             body: JSON.stringify({ status: newStatus, reason: 'Updated via portal' })
           }
         );
         setTasks(tasks.map(t => t.id === taskId ? {...t, status: newStatus} : t));
         setToast({ msg: '✅ Task status updated', type: 'success' });
       } catch (err) {
         setToast({ msg: '❌ Update failed', type: 'error' });
       }
     };
     ```

6. **Task Approval/Rejection**
   - Show only for tasks with status SUBMITTED
   - Buttons: [Approve] [Reject]
   - On click:
     ```javascript
     const handleApproveTask = async (taskId, approve) => {
       try {
         await apiFetch(
           `http://localhost:8080/api/v1/tasks/${taskId}/approve`,
           jwt,
           {
             method: 'POST',
             body: JSON.stringify({
               approved: approve,
               reason: approve ? 'Approved via portal' : 'Rejected via portal'
             })
           }
         );
         const updatedStatus = approve ? 'COMPLETED' : 'ASSIGNED';
         setTasks(tasks.map(t => t.id === taskId ? {...t, status: updatedStatus} : t));
         setToast({ msg: `✅ Task ${approve ? 'approved' : 'rejected'}`, type: 'success' });
       } catch (err) {
         setToast({ msg: '❌ Action failed', type: 'error' });
       }
     };
     ```

7. **Task History Modal**
   - Trigger: Click on task title
   - Fetch: `GET /api/v1/tasks/{taskId}/history`
   - Display timeline of status changes with timestamps and reasons

---

### TASK 2.4: Wire Employee Directory Tab

**Location**: `Frontend/src/main.jsx` → Employees Tab Render Block

**Endpoints Required**:
```
GET /api/v1/users                              (list all employees)
POST /api/v1/users/invite                      (invite new)
PUT /api/v1/users/{userId}/role                (change role)
PUT /api/v1/users/{userId}/status              (suspend/activate)
GET /api/v1/roles                              (get available roles)
```

**Components to Update**:

1. **Load Employees on Tab Switch**
   ```javascript
   React.useEffect(() => {
     if (screen === 'employees' && jwt) {
       Promise.all([
         apiFetch('http://localhost:8080/api/v1/users', jwt),
         apiFetch('http://localhost:8080/api/v1/roles', jwt)
       ])
         .then(([usersData, rolesData]) => {
           setEmployees(Array.isArray(usersData) ? usersData : usersData.data || []);
           setAvailableRoles(Array.isArray(rolesData) ? rolesData : rolesData.data || []);
         })
         .catch(err => setToast({ msg: 'Failed to load employees', type: 'error' }));
     }
   }, [screen, jwt]);
   ```

2. **Department Filter**
   - Add state: `const [deptFilter, setDeptFilter] = React.useState('all');`
   - Populate with departments list
   - Filter employees by department

3. **Invite Employee Modal**
   - Trigger: Click "Invite Employee" button
   - Add modal state: `const [showInviteModal, setShowInviteModal] = React.useState(false);`
   - Form fields:
     ```javascript
     const [inviteForm, setInviteForm] = React.useState({
       email: '',
       name: '',
       roleId: '',
       departmentId: ''
     });
     ```
   - Validation: email format, role & department required
   - On submit:
     ```javascript
     const handleInviteEmployee = async () => {
       try {
         const response = await apiFetch(
           'http://localhost:8080/api/v1/users/invite',
           jwt,
           {
             method: 'POST',
             body: JSON.stringify({
               email: inviteForm.email,
               displayName: inviteForm.name,
               roleIds: [inviteForm.roleId],
               departmentId: inviteForm.departmentId,
               businessId: business.id
             })
           }
         );
         setEmployees([...employees, response]);
         setShowInviteModal(false);
         setToast({ msg: '✅ Invite sent to ' + inviteForm.email, type: 'success' });
         setInviteForm({ email: '', name: '', roleId: '', departmentId: '' });
       } catch (err) {
         setToast({ msg: '❌ Invite failed: ' + err.message, type: 'error' });
       }
     };
     ```

4. **Employee List Display**
   - Render each employee with:
     - Display name
     - Email
     - Current role (dropdown for change)
     - Department (dropdown for transfer)
     - Status (Active/Suspended toggle)
     - Last login date
     - Action buttons: [Edit] [Suspend/Activate] [Delete]

5. **Change Employee Role**
   - Dropdown: populated with `availableRoles`
   - On change:
     ```javascript
     const handleChangeRole = async (userId, newRoleId) => {
       try {
         await apiFetch(
           `http://localhost:8080/api/v1/users/${userId}/role`,
           jwt,
           {
             method: 'PUT',
             body: JSON.stringify({ roleId: newRoleId })
           }
         );
         setEmployees(employees.map(e => 
           e.id === userId ? {...e, roleIds: [newRoleId]} : e
         ));
         setToast({ msg: '✅ Role updated', type: 'success' });
       } catch (err) {
         setToast({ msg: '❌ Update failed', type: 'error' });
       }
     };
     ```

6. **Toggle Employee Status**
   - Show: Active/Suspended toggle
   - On toggle:
     ```javascript
     const handleToggleStatus = async (userId, currentStatus) => {
       const newStatus = currentStatus === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
       try {
         await apiFetch(
           `http://localhost:8080/api/v1/users/${userId}/status`,
           jwt,
           {
             method: 'PUT',
             body: JSON.stringify({ status: newStatus, reason: 'Updated via portal' })
           }
         );
         setEmployees(employees.map(e => 
           e.id === userId ? {...e, status: newStatus} : e
         ));
         setToast({ 
           msg: `✅ Employee ${newStatus === 'ACTIVE' ? 'activated' : 'suspended'}`, 
           type: 'success' 
         });
       } catch (err) {
         setToast({ msg: '❌ Status change failed', type: 'error' });
       }
     };
     ```

---

### TASK 2.5: Wire Departments & Roles Tab

**Location**: `Frontend/src/main.jsx` → Departments Tab Render Block

**Endpoints Required**:
```
GET /api/v1/departments                        (list all)
POST /api/v1/departments                       (create new)
GET /api/v1/roles                              (list all)
POST /api/v1/roles                             (create new)
POST /api/v1/roles/{roleId}/permissions        (add permissions)
DELETE /api/v1/roles/{roleId}/permissions/{permission}  (remove permissions)
```

**Components to Update**:

1. **Load Departments & Roles on Tab Switch**
   ```javascript
   React.useEffect(() => {
     if (screen === 'departments' && jwt) {
       Promise.all([
         apiFetch('http://localhost:8080/api/v1/departments', jwt),
         apiFetch('http://localhost:8080/api/v1/roles', jwt)
       ])
         .then(([depts, roles]) => {
           setDepartments(Array.isArray(depts) ? depts : depts.data || []);
           setAvailableRoles(Array.isArray(roles) ? roles : roles.data || []);
         })
         .catch(err => setToast({ msg: 'Failed to load', type: 'error' }));
     }
   }, [screen, jwt]);
   ```

2. **Departments Section**
   - Add modal state: `const [showAddDeptModal, setShowAddDeptModal] = React.useState(false);`
   - Form field: `const [newDeptName, setNewDeptName] = React.useState('');`
   - Create Department:
     ```javascript
     const handleCreateDepartment = async () => {
       try {
         const response = await apiFetch(
           'http://localhost:8080/api/v1/departments',
           jwt,
           {
             method: 'POST',
             body: JSON.stringify({
               name: newDeptName,
               businessId: business.id
             })
           }
         );
         setDepartments([...departments, response]);
         setShowAddDeptModal(false);
         setToast({ msg: '✅ Department created', type: 'success' });
         setNewDeptName('');
       } catch (err) {
         setToast({ msg: '❌ Failed: ' + err.message, type: 'error' });
       }
     };
     ```
   - Display departments in list/grid
   - Show: department name, head name (if assigned), member count
   - Actions: [Edit Name] [Delete]

3. **Roles Section**
   - Add modal state: `const [showAddRoleModal, setShowAddRoleModal] = React.useState(false);`
   - Form fields:
     ```javascript
     const [newRole, setNewRole] = React.useState({
       name: '',
       permissions: []
     });
     ```
   - Available permissions to select:
     ```
     TASK_CREATE, TASK_EDIT, TASK_DELETE, TASK_APPROVE
     USER_INVITE, USER_EDIT, USER_DELETE, USER_SUSPEND
     DEPARTMENT_CREATE, DEPARTMENT_EDIT, DEPARTMENT_DELETE
     ANALYTICS_VIEW, SETTINGS_EDIT
     ```
   - Create Role:
     ```javascript
     const handleCreateRole = async () => {
       try {
         const response = await apiFetch(
           'http://localhost:8080/api/v1/roles',
           jwt,
           {
             method: 'POST',
             body: JSON.stringify({
               name: newRole.name,
               businessId: business.id
             })
           }
         );
         // Add permissions
         for (const permission of newRole.permissions) {
           await apiFetch(
             `http://localhost:8080/api/v1/roles/${response.id}/permissions`,
             jwt,
             {
               method: 'POST',
               body: JSON.stringify({ permission })
             }
           );
         }
         response.permissions = newRole.permissions;
         setAvailableRoles([...availableRoles, response]);
         setShowAddRoleModal(false);
         setToast({ msg: '✅ Role created', type: 'success' });
         setNewRole({ name: '', permissions: [] });
       } catch (err) {
         setToast({ msg: '❌ Failed: ' + err.message, type: 'error' });
       }
     };
     ```
   - Display roles in list
   - Show: role name, permissions list, member count
   - Actions: [Edit Permissions] [Delete]

4. **Permission Management for Roles**
   - Show checkboxes for each available permission
   - On change:
     ```javascript
     const handleTogglePermission = async (roleId, permission, isAdding) => {
       try {
         if (isAdding) {
           await apiFetch(
             `http://localhost:8080/api/v1/roles/${roleId}/permissions`,
             jwt,
             {
               method: 'POST',
               body: JSON.stringify({ permission })
             }
           );
         } else {
           await apiFetch(
             `http://localhost:8080/api/v1/roles/${roleId}/permissions/${permission}`,
             jwt,
             { method: 'DELETE' }
           );
         }
         // Update local state
         setAvailableRoles(availableRoles.map(r => 
           r.id === roleId ? {
             ...r,
             permissions: isAdding 
               ? [...r.permissions, permission]
               : r.permissions.filter(p => p !== permission)
           } : r
         ));
         setToast({ msg: '✅ Permissions updated', type: 'success' });
       } catch (err) {
         setToast({ msg: '❌ Update failed', type: 'error' });
       }
     };
     ```

---

### TASK 2.6: Wire Profile & Settings Tab

**Location**: `Frontend/src/main.jsx` → Profile Tab Render Block

**Endpoints Required**:
```
GET /api/v1/users/me                           (get current user)
PUT /api/v1/users/me                           (update profile)
PUT /api/v1/businesses/{businessId}            (update business info)
GET /api/v1/telegram/status                    (get Telegram webhook status)
```

**Components to Update**:

1. **Load User & Business Profile on Tab Switch**
   ```javascript
   React.useEffect(() => {
     if (screen === 'profile' && jwt) {
       Promise.all([
         apiFetch('http://localhost:8080/api/v1/users/me', jwt),
         apiFetch('http://localhost:8080/api/v1/telegram/status', jwt)
       ])
         .then(([userData, tgStatus]) => {
           setUser(userData);
           setTelegramStatus(tgStatus);
           if (business) {
             setBusiness(userData.business || business);
           }
         })
         .catch(err => setToast({ msg: 'Failed to load profile', type: 'error' }));
     }
   }, [screen, jwt]);
   ```

2. **User Profile Section**
   - Display: name, email, role, department, phone
   - Editable fields: phone, display name
   - Edit button toggles edit mode
   - Save changes:
     ```javascript
     const handleUpdateProfile = async () => {
       try {
         const response = await apiFetch(
           'http://localhost:8080/api/v1/users/me',
           jwt,
           {
             method: 'PUT',
             body: JSON.stringify({
               displayName: user.displayName,
               phone: user.phone
             })
           }
         );
         setUser(response);
         setToast({ msg: '✅ Profile updated', type: 'success' });
       } catch (err) {
         setToast({ msg: '❌ Update failed: ' + err.message, type: 'error' });
       }
     };
     ```

3. **Business Profile Section**
   - Display: business name, created date, member count, subscription tier
   - Editable: business name
   - Update business:
     ```javascript
     const handleUpdateBusiness = async (newName) => {
       try {
         const response = await apiFetch(
           `http://localhost:8080/api/v1/businesses/${business.id}`,
           jwt,
           {
             method: 'PUT',
             body: JSON.stringify({ name: newName })
           }
         );
         setBusiness(response);
         setToast({ msg: '✅ Business info updated', type: 'success' });
       } catch (err) {
         setToast({ msg: '❌ Update failed: ' + err.message, type: 'error' });
       }
     };
     ```

4. **Telegram Webhook Status**
   - Display status indicator:
     - 🟢 Connected if `telegramStatus.isActive === true`
     - 🔴 Disconnected if `telegramStatus.isActive === false`
   - Show webhook URL: `telegramStatus.webhookUrl`
   - Show last activity: `telegramStatus.lastActivity`
   - Manual refresh button:
     ```javascript
     const handleRefreshTelegramStatus = async () => {
       try {
         const status = await apiFetch(
           'http://localhost:8080/api/v1/telegram/status',
           jwt
         );
         setTelegramStatus(status);
         setToast({ msg: '✅ Status refreshed', type: 'success' });
       } catch (err) {
         setToast({ msg: '❌ Refresh failed', type: 'error' });
       }
     };
     ```

5. **Account Security Section**
   - Password change button
   - Two-factor authentication toggle (if applicable)
   - Session management: show active sessions, logout option

6. **Logout Button**
   - Clear JWT from sessionStorage
   - Clear all state
   - Return to login phase
   - Function:
     ```javascript
     const handleLogout = () => {
       sessionStorage.removeItem('jwt_token');
       setJwt('');
       setPhase('start');
       setScreen('overview');
       setUser(null);
       setBusiness(null);
       setToast({ msg: 'Logged out successfully', type: 'success' });
     };
     ```

---

## 🔧 Critical Implementation Details

### Error Handling for All Endpoints
Every API call should follow this pattern:
```javascript
try {
  const response = await apiFetch(url, jwt, options);
  // Handle success
  setToast({ msg: '✅ Success message', type: 'success' });
} catch (error) {
  // Handle error
  const errorMsg = error.message || 'Unknown error occurred';
  setToast({ msg: `❌ ${errorMsg}`, type: 'error' });
  console.error('API Error:', error);
}
```

### Loading States
Add loading state for async operations:
```javascript
const [loading, setLoading] = React.useState(false);

// In async function:
setLoading(true);
try {
  // API call
} finally {
  setLoading(false);
}

// In JSX: Disable buttons while loading
<button disabled={loading}>{loading ? 'Loading...' : 'Submit'}</button>
```

### Form Validation
Validate required fields before submission:
```javascript
const validateForm = () => {
  const errors = [];
  if (!formData.title?.trim()) errors.push('Title is required');
  if (!formData.assigneeId) errors.push('Assignee is required');
  if (!formData.dueDate) errors.push('Due date is required');
  
  if (errors.length > 0) {
    setToast({ msg: errors.join('\n'), type: 'error' });
    return false;
  }
  return true;
};

// Use before submitting:
if (!validateForm()) return;
```

### Date/Time Formatting
```javascript
// Format for display
const formatDate = (isoString) => {
  if (!isoString) return 'No date';
  return new Date(isoString).toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  });
};

// Format for input
const formatDateForInput = (isoString) => {
  if (!isoString) return '';
  return isoString.split('T')[0]; // Returns YYYY-MM-DD
};
```

### Color Coding for Status Badges
```javascript
const getStatusColor = (status) => {
  return {
    'ASSIGNED': '#3b82f6',    // blue
    'IN_PROGRESS': '#f59e0b', // amber
    'SUBMITTED': '#8b5cf6',   // purple
    'COMPLETED': '#10b981',   // green
    'ACTIVE': '#10b981',      // green
    'SUSPENDED': '#ef4444'    // red
  }[status] || '#6b7280';     // gray default
};
```

---

## ✅ Testing Checklist for Phase 2

- [ ] Overview tab loads and displays data from endpoints
- [ ] Analytics tab displays metrics and can export reports
- [ ] Task Management: Can create, view, filter, update status, approve/reject tasks
- [ ] Employees: Can view, invite, change roles, suspend/activate
- [ ] Departments & Roles: Can create, edit permissions
- [ ] Profile: Can view and edit user profile, business info, Telegram status
- [ ] All error messages display correctly
- [ ] Loading states work for all async operations
- [ ] Token refresh handling if JWT expires
- [ ] Navigation between tabs works smoothly
- [ ] Mobile responsiveness check

---

## 📌 Frontend API Base URL Configuration

**Current hardcoded URL**: `http://localhost:8080`

**Recommended improvement** (for future phases):
```javascript
// At top of main.jsx
const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

// Then use:
apiFetch(`${API_BASE_URL}/api/v1/tasks`, jwt)
```

**Environment variable**: Create `.env` file in `Frontend/` directory:
```
REACT_APP_API_URL=http://localhost:8080
```

---

## 🎨 UI/UX Considerations

1. **Modals**: Use consistent modal component with close button
2. **Buttons**: Disabled state during loading, hover effects
3. **Forms**: Clear labels, required field indicators (*)
4. **Tables**: Sortable columns, pagination for large lists
5. **Empty States**: Show "No data" message when lists are empty
6. **Toast Notifications**: Auto-dismiss after 4 seconds, stack multiple
7. **Responsive Design**: Test on mobile, tablet, desktop
8. **Accessibility**: ARIA labels, keyboard navigation

