# Phase 3: Backend API Completeness Audit — Detailed Implementation Guide

**Objective**: Verify all required REST endpoints exist and are fully functional with proper role-based access control.

---

## 📋 Endpoints Audit Checklist

### Legend
- ✅ = Endpoint exists and is complete
- ⚠️ = Endpoint exists but needs fixes/enhancements
- ❌ = Endpoint missing or incomplete
- 🔐 = Requires role-based access control

---

## 📊 1. ANALYTICS ENDPOINTS

### 1.1 Get Analytics Summary
**Endpoint**: `GET /api/v1/analytics/summary`

**Status**: ❌ NEEDS VERIFICATION

**Description**: Get high-level overview of business metrics (used in Overview dashboard)

**Required Response Fields**:
```json
{
  "completionRate": 87.5,
  "totalTasks": 24,
  "completedTasks": 21,
  "overdueTasks": 3,
  "totalEmployees": 12,
  "activeEmployees": 11,
  "totalDepartments": 4,
  "overallScore": 92,
  "lastUpdated": "2026-07-19T15:30:00Z"
}
```

**Implementation Checklist**:
- [ ] Verify endpoint exists in `AnalyticsController`
- [ ] Calculate completion rate: `completedTasks / totalTasks * 100`
- [ ] Count overdue tasks: tasks where `dueDate < now()` and status ≠ COMPLETED
- [ ] Query active employees: users where `status = 'ACTIVE'`
- [ ] Cache results for 5 minutes to avoid expensive calculations
- [ ] Access control: Role.EMPLOYEE and above can view

**Location to Check/Create**: `backend/src/main/java/com/contextcraft/portal/controller/AnalyticsController.java`

---

### 1.2 Get Detailed Analytics
**Endpoint**: `GET /api/v1/analytics?dateRange=monthly&department=all`

**Status**: ❌ NEEDS VERIFICATION

**Description**: Get detailed metrics for Analytics tab with optional filters

**Query Parameters**:
- `dateRange` (optional): daily, weekly, monthly, quarterly, yearly (default: monthly)
- `department` (optional): Department ID or "all" (default: all)
- `startDate` (optional): ISO 8601 date string
- `endDate` (optional): ISO 8601 date string

**Required Response Fields**:
```json
{
  "period": "monthly",
  "startDate": "2026-06-19T00:00:00Z",
  "endDate": "2026-07-19T23:59:59Z",
  "completionRate": 87.5,
  "averageTaskTime": 3.2,
  "overdueCount": 5,
  "overallScore": 92,
  "departmentMetrics": [
    {
      "departmentId": "uuid-1",
      "department": "Engineering",
      "score": 95,
      "completionRate": 90,
      "taskCount": 12,
      "completedCount": 11
    },
    {
      "departmentId": "uuid-2",
      "department": "Sales",
      "score": 88,
      "completionRate": 85,
      "taskCount": 8,
      "completedCount": 7
    }
  ],
  "dailyTrend": [
    { "date": "2026-07-15", "tasksCreated": 3, "tasksCompleted": 2 },
    { "date": "2026-07-16", "tasksCreated": 5, "tasksCompleted": 4 }
  ]
}
```

**Implementation Checklist**:
- [ ] Parse date range parameters and apply filters
- [ ] Calculate metrics per department
- [ ] Build daily/weekly/monthly trend data
- [ ] Apply business timezone for date calculations
- [ ] Cache for 5 minutes
- [ ] Access control: Role.MANAGER and above can view all departments; others see only their own

---

### 1.3 Export Analytics Report
**Endpoint**: `GET /api/v1/analytics/export?format=pdf&dateRange=monthly`

**Status**: ❌ NEEDS VERIFICATION

**Description**: Generate and download analytics report as PDF/CSV

**Query Parameters**:
- `format` (required): pdf or csv
- `dateRange` (optional): monthly (default)
- `department` (optional): Department ID or "all"

**Response**:
- Content-Type: `application/pdf` or `text/csv`
- Content-Disposition: `attachment; filename="report_monthly_2026-07-19.pdf"`

**Implementation Checklist**:
- [ ] Verify endpoint exists in AnalyticsController
- [ ] PDF generation: Use library like iText or Apache PDFBox
- [ ] CSV generation: Use library like OpenCSV
- [ ] Include header with business name, date range, generated date
- [ ] Include all metrics from detailed analytics endpoint
- [ ] Include department breakdowns, trends, performance summaries
- [ ] Access control: Role.MANAGER and above

**Dependencies to verify/add**:
```xml
<!-- For PDF generation -->
<dependency>
  <groupId>com.itextpdf</groupId>
  <artifactId>itext-core</artifactId>
  <version>8.0.4</version>
</dependency>

<!-- For CSV generation -->
<dependency>
  <groupId>org.apache.commons</groupId>
  <artifactId>commons-csv</artifactId>
  <version>1.10.0</version>
</dependency>
```

---

## 👥 2. USER/EMPLOYEE ENDPOINTS

### 2.1 Get All Users (Employees)
**Endpoint**: `GET /api/v1/users?department=all&status=ACTIVE`

**Status**: ⚠️ VERIFY & ENHANCE

**Description**: List all employees in business with optional filters

**Query Parameters**:
- `department` (optional): Department ID or "all"
- `status` (optional): ACTIVE, SUSPENDED, INACTIVE (default: ACTIVE)
- `role` (optional): Role ID
- `page` (optional): Page number (default: 0)
- `size` (optional): Page size (default: 50)
- `search` (optional): Search by name or email

**Required Response Format**:
```json
{
  "content": [
    {
      "id": "uuid",
      "email": "john@company.com",
      "displayName": "John Doe",
      "phone": "+1-555-123-4567",
      "departmentId": "uuid",
      "department": "Engineering",
      "roleIds": ["uuid1", "uuid2"],
      "roles": ["Developer", "Team Lead"],
      "status": "ACTIVE",
      "createdAt": "2026-01-15T10:00:00Z",
      "lastLoginAt": "2026-07-18T14:30:00Z"
    }
  ],
  "totalElements": 42,
  "totalPages": 1,
  "currentPage": 0,
  "pageSize": 50
}
```

**Implementation Checklist**:
- [ ] Verify endpoint exists in UserController
- [ ] Apply filters: department, status, role, search
- [ ] Implement pagination (Pageable support)
- [ ] Search by displayName or email (case-insensitive)
- [ ] Include role names in response (join from UserRole table)
- [ ] Order by displayName ASC
- [ ] Access control: Role.MANAGER and above; EMPLOYEE sees only own data
- [ ] Never include password hashes in response

**Location**: `backend/src/main/java/com/contextcraft/portal/controller/UserController.java`

---

### 2.2 Get Single User Details
**Endpoint**: `GET /api/v1/users/{userId}`

**Status**: ⚠️ VERIFY

**Description**: Get detailed information for a single user

**Path Parameters**:
- `userId` (required): UUID of user

**Response Fields** (same as 2.1 but with extended fields):
```json
{
  "id": "uuid",
  "email": "john@company.com",
  "displayName": "John Doe",
  "phone": "+1-555-123-4567",
  "departmentId": "uuid",
  "department": "Engineering",
  "roleIds": ["uuid1"],
  "roles": ["Developer"],
  "status": "ACTIVE",
  "createdAt": "2026-01-15T10:00:00Z",
  "lastLoginAt": "2026-07-18T14:30:00Z",
  "permissions": ["TASK_CREATE", "TASK_EDIT", "TASK_APPROVE", "USER_VIEW"],
  "tasksAssigned": 5,
  "tasksCompleted": 3
}
```

**Implementation Checklist**:
- [ ] Verify endpoint exists
- [ ] Return 404 if user not found
- [ ] Include aggregated permissions from all assigned roles
- [ ] Count tasks assigned and completed for this user
- [ ] Access control: User can view own profile; MANAGER+ can view any

---

### 2.3 Create/Invite User
**Endpoint**: `POST /api/v1/users/invite`

**Status**: ❌ CHECK & POSSIBLY ENHANCE

**Description**: Invite new employee to business

**Request Body**:
```json
{
  "email": "newuser@company.com",
  "displayName": "New User",
  "roleIds": ["uuid-role-1"],
  "departmentId": "uuid-dept-1",
  "businessId": "uuid-business",
  "invitationMessage": "Optional custom message"
}
```

**Response**: (201 Created)
```json
{
  "id": "uuid",
  "email": "newuser@company.com",
  "displayName": "New User",
  "status": "PENDING_INVITATION",
  "invitationToken": "token-xyz",
  "invitationExpiresAt": "2026-07-26T00:00:00Z",
  "createdAt": "2026-07-19T15:30:00Z"
}
```

**Implementation Checklist**:
- [ ] Verify endpoint exists in UserController
- [ ] Validate email format
- [ ] Check email not already registered for this business
- [ ] Verify role IDs exist in business
- [ ] Verify department ID exists in business
- [ ] Create User record with status=PENDING_INVITATION
- [ ] Generate secure invitation token (JWT-based, 7-day expiry)
- [ ] Send email invitation with token link
- [ ] Access control: Role.CEO and Role.MANAGER only
- [ ] Return 409 if email already invited/exists

**Email Template** (to send):
```
Subject: You're invited to join [Business Name]

Hello [DisplayName],

You've been invited to join [BusinessName] as a [RoleName] in the [DepartmentName] department.

Click here to accept: [Invitation URL with token]

This invitation expires in 7 days.

Best regards,
The [BusinessName] Team
```

---

### 2.4 Update User Role
**Endpoint**: `PUT /api/v1/users/{userId}/role`

**Status**: ❌ CHECK & POSSIBLY CREATE

**Description**: Change user's role(s) in business

**Path Parameters**:
- `userId` (required): UUID of user

**Request Body** (Option A: Replace all roles):
```json
{
  "roleIds": ["uuid-role-1", "uuid-role-2"]
}
```

**Request Body** (Option B: Add/remove single role):
```json
{
  "action": "add|remove",
  "roleId": "uuid-role-1"
}
```

**Response**: (200 OK)
```json
{
  "id": "uuid",
  "displayName": "John Doe",
  "roleIds": ["uuid-role-1", "uuid-role-2"],
  "roles": ["Developer", "Team Lead"],
  "updatedAt": "2026-07-19T15:35:00Z"
}
```

**Implementation Checklist**:
- [ ] Verify endpoint exists or create if missing
- [ ] Validate all roleIds exist in business
- [ ] Prevent removing CEO if only CEO in business
- [ ] Update UserRole junction table
- [ ] Log role change to audit trail (with old/new roles, timestamp, actor)
- [ ] Access control: CEO and MANAGER only
- [ ] Return 404 if user not found
- [ ] Return 400 if role not found

**Location**: `backend/src/main/java/com/contextcraft/portal/controller/UserController.java`

**Database Changes** (if endpoint doesn't exist):
```sql
-- Verify UserRole table structure:
-- user_id (FK to User)
-- role_id (FK to Role)
-- created_at timestamp
-- created_by_id (who assigned this role)
```

---

### 2.5 Update User Status (Suspend/Activate)
**Endpoint**: `PUT /api/v1/users/{userId}/status`

**Status**: ❌ CHECK & POSSIBLY CREATE

**Description**: Suspend or reactivate user account

**Path Parameters**:
- `userId` (required): UUID of user

**Request Body**:
```json
{
  "status": "ACTIVE|SUSPENDED|INACTIVE",
  "reason": "User on leave",
  "suspendUntil": "2026-08-19T00:00:00Z"
}
```

**Response**: (200 OK)
```json
{
  "id": "uuid",
  "displayName": "John Doe",
  "status": "SUSPENDED",
  "suspendReason": "User on leave",
  "suspendUntil": "2026-08-19T00:00:00Z",
  "updatedAt": "2026-07-19T15:40:00Z"
}
```

**Implementation Checklist**:
- [ ] Verify endpoint exists or create if missing
- [ ] Validate status value (enum: ACTIVE, SUSPENDED, INACTIVE)
- [ ] If SUSPENDED: save reason and optional unsuspend date
- [ ] If ACTIVE: clear suspension dates
- [ ] Prevent suspending own account
- [ ] Prevent suspending if only active CEO
- [ ] Log status change to audit trail
- [ ] Invalidate user's active sessions/tokens
- [ ] Access control: CEO and MANAGER only
- [ ] Return 400 if invalid transition

**Location**: `backend/src/main/java/com/contextcraft/portal/controller/UserController.java`

**Database Changes** (if fields missing from User table):
```sql
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'ACTIVE';
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS suspend_reason VARCHAR(255);
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS suspend_until TIMESTAMP;
```

---

### 2.6 Get Current User (/me endpoint)
**Endpoint**: `GET /api/v1/users/me`

**Status**: ✅ LIKELY EXISTS

**Description**: Get authenticated user's own profile

**Response**:
```json
{
  "id": "uuid",
  "email": "current@company.com",
  "displayName": "Current User",
  "phone": "+1-555-987-6543",
  "departmentId": "uuid",
  "department": "Engineering",
  "roleIds": ["uuid"],
  "roles": ["Developer"],
  "businessId": "uuid",
  "business": {
    "id": "uuid",
    "name": "My Business",
    "createdAt": "2025-01-01T00:00:00Z"
  },
  "status": "ACTIVE",
  "lastLoginAt": "2026-07-19T14:30:00Z",
  "createdAt": "2026-01-15T10:00:00Z"
}
```

**Implementation Checklist**:
- [ ] Verify endpoint exists
- [ ] Extract userId from JWT token
- [ ] Include business object with minimal info
- [ ] Access control: Any authenticated user

---

### 2.7 Update Current User Profile
**Endpoint**: `PUT /api/v1/users/me`

**Status**: ⚠️ LIKELY EXISTS BUT VERIFY FIELDS

**Description**: Update own user profile information

**Request Body** (updateable fields only):
```json
{
  "displayName": "Updated Name",
  "phone": "+1-555-999-9999"
}
```

**NOT updatable**: email, role, department, status (require separate endpoints)

**Response**: (200 OK)
```json
{
  "id": "uuid",
  "email": "current@company.com",
  "displayName": "Updated Name",
  "phone": "+1-555-999-9999",
  "status": "ACTIVE",
  "updatedAt": "2026-07-19T15:45:00Z"
}
```

**Implementation Checklist**:
- [ ] Verify endpoint exists
- [ ] Only allow updating: displayName, phone, profilePicture (if applicable)
- [ ] Validate phone format
- [ ] Trim whitespace from displayName
- [ ] Prevent empty/null displayName
- [ ] Access control: Any authenticated user (can only update own)

---

## 📋 3. TASK ENDPOINTS

### 3.1 Get All Tasks
**Endpoint**: `GET /api/v1/tasks?status=all&assignee=all&department=all`

**Status**: ✅ LIKELY EXISTS

**Description**: List all tasks in business with optional filters

**Query Parameters**:
- `status` (optional): ASSIGNED, IN_PROGRESS, SUBMITTED, COMPLETED, or "all"
- `assignee` (optional): User ID
- `department` (optional): Department ID
- `priority` (optional): HIGH, MEDIUM, LOW
- `overdueOnly` (optional): true/false
- `page` (optional): Page number
- `size` (optional): Page size

**Response Format**:
```json
{
  "content": [
    {
      "id": "uuid",
      "title": "Audit Q3 finances",
      "description": "Complete quarterly financial audit",
      "status": "IN_PROGRESS",
      "priority": "HIGH",
      "createdById": "uuid",
      "createdBy": "Manager Name",
      "assigneeId": "uuid",
      "assignee": "John Doe",
      "departmentId": "uuid",
      "department": "Finance",
      "dueDate": "2026-07-31T23:59:59Z",
      "createdAt": "2026-07-01T10:00:00Z",
      "updatedAt": "2026-07-19T14:00:00Z",
      "isOverdue": false
    }
  ],
  "totalElements": 42,
  "totalPages": 1,
  "currentPage": 0,
  "pageSize": 50
}
```

**Implementation Checklist**:
- [ ] Verify endpoint exists in TaskController
- [ ] Apply all filters correctly
- [ ] Calculate `isOverdue`: `dueDate < now() AND status != COMPLETED`
- [ ] Pagination working correctly
- [ ] Access control: Users see own tasks + team leads see team tasks; managers see all
- [ ] Order by dueDate ASC, then priority DESC

---

### 3.2 Get Single Task
**Endpoint**: `GET /api/v1/tasks/{taskId}`

**Status**: ✅ LIKELY EXISTS

**Description**: Get detailed task information

**Path Parameters**:
- `taskId` (required): UUID of task

**Response**:
```json
{
  "id": "uuid",
  "title": "Audit Q3 finances",
  "description": "Complete quarterly financial audit",
  "status": "IN_PROGRESS",
  "priority": "HIGH",
  "createdById": "uuid",
  "createdBy": "Manager Name",
  "assigneeId": "uuid",
  "assignee": "John Doe",
  "departmentId": "uuid",
  "department": "Finance",
  "dueDate": "2026-07-31T23:59:59Z",
  "createdAt": "2026-07-01T10:00:00Z",
  "updatedAt": "2026-07-19T14:00:00Z",
  "isOverdue": false,
  "attachments": [
    {
      "id": "uuid",
      "fileName": "template.xlsx",
      "fileSize": 45678,
      "uploadedAt": "2026-07-19T10:00:00Z",
      "uploadedBy": "uploader name"
    }
  ]
}
```

**Implementation Checklist**:
- [ ] Verify endpoint exists
- [ ] Include attachment list
- [ ] Access control: User can view if they're assignee/creator/manager
- [ ] Return 404 if not found

---

### 3.3 Create Task
**Endpoint**: `POST /api/v1/tasks`

**Status**: ✅ LIKELY EXISTS

**Description**: Create new task

**Request Body**:
```json
{
  "title": "New Task Title",
  "description": "Task description",
  "assigneeId": "uuid",
  "priority": "MEDIUM",
  "dueDate": "2026-07-31T23:59:59Z",
  "departmentId": "uuid"
}
```

**Response**: (201 Created)
```json
{
  "id": "uuid",
  "title": "New Task Title",
  "status": "ASSIGNED",
  "priority": "MEDIUM",
  "createdAt": "2026-07-19T15:50:00Z"
}
```

**Implementation Checklist**:
- [ ] Verify endpoint exists
- [ ] Validate required fields: title, assignee
- [ ] Validate priority enum
- [ ] Validate dueDate is future date
- [ ] Create TaskHistory record with action=CREATED
- [ ] Access control: MANAGER and above
- [ ] Return 400 if assignee/department not found

---

### 3.4 Update Task Status
**Endpoint**: `PUT /api/v1/tasks/{taskId}/status`

**Status**: ❌ CHECK & POSSIBLY CREATE

**Description**: Update task status (workflow)

**Path Parameters**:
- `taskId` (required): UUID of task

**Request Body**:
```json
{
  "status": "IN_PROGRESS|SUBMITTED|COMPLETED",
  "reason": "Optional reason for status change"
}
```

**Allowed Transitions**:
```
ASSIGNED → IN_PROGRESS (by assignee)
IN_PROGRESS → SUBMITTED (by assignee)
SUBMITTED → ASSIGNED|COMPLETED|IN_PROGRESS (by reviewer/manager)
(Cannot go backwards except SUBMITTED → ASSIGNED)
```

**Response**: (200 OK)
```json
{
  "id": "uuid",
  "title": "Task Title",
  "status": "IN_PROGRESS",
  "updatedAt": "2026-07-19T15:55:00Z"
}
```

**Implementation Checklist**:
- [ ] Verify endpoint exists or create
- [ ] Enforce state machine transitions
- [ ] Create TaskHistory record with state change
- [ ] Validate status enum
- [ ] Access control: Assignee can move from ASSIGNED→IN_PROGRESS→SUBMITTED; Manager can move SUBMITTED
- [ ] Return 400 if invalid transition
- [ ] Send notification to approver if status=SUBMITTED

**Location**: `backend/src/main/java/com/contextcraft/portal/controller/TaskController.java`

**Database Consideration**:
```sql
-- Ensure TaskHistory table captures all state changes:
-- task_id (FK)
-- old_status
-- new_status
-- reason
-- changed_by_id (who made the change)
-- changed_at timestamp
```

---

### 3.5 Approve/Reject Task
**Endpoint**: `POST /api/v1/tasks/{taskId}/approve`

**Status**: ✅ VERIFY

**Description**: Approve or reject submitted task

**Path Parameters**:
- `taskId` (required): UUID of task

**Request Body**:
```json
{
  "approved": true|false,
  "reason": "Approved as per requirements",
  "feedback": "Optional detailed feedback"
}
```

**Response**: (200 OK)
```json
{
  "id": "uuid",
  "title": "Task Title",
  "status": "COMPLETED",
  "approvedAt": "2026-07-19T16:00:00Z",
  "approverName": "Reviewer Name"
}
```

**Implementation Checklist**:
- [ ] Verify endpoint exists
- [ ] Only allow on SUBMITTED tasks
- [ ] If approved: set status=COMPLETED, completedAt=now()
- [ ] If rejected: set status=ASSIGNED, assignee notified
- [ ] Create TaskHistory record
- [ ] Access control: MANAGER and above only
- [ ] Return 400 if task not in SUBMITTED status

---

### 3.6 Get Task History
**Endpoint**: `GET /api/v1/tasks/{taskId}/history`

**Status**: ✅ VERIFY

**Description**: Get audit trail of all changes to task

**Path Parameters**:
- `taskId` (required): UUID of task

**Response**:
```json
[
  {
    "id": "uuid",
    "taskId": "uuid",
    "action": "CREATED",
    "oldStatus": null,
    "newStatus": null,
    "reason": "Task created",
    "changedBy": "Manager Name",
    "changedAt": "2026-07-01T10:00:00Z"
  },
  {
    "id": "uuid",
    "taskId": "uuid",
    "action": "ASSIGNED_TO",
    "oldStatus": null,
    "newStatus": null,
    "reason": "Assigned to John Doe",
    "changedBy": "Manager Name",
    "changedAt": "2026-07-01T10:15:00Z"
  },
  {
    "id": "uuid",
    "taskId": "uuid",
    "action": "STATUS_CHANGED",
    "oldStatus": "ASSIGNED",
    "newStatus": "IN_PROGRESS",
    "reason": "Work started",
    "changedBy": "John Doe",
    "changedAt": "2026-07-19T14:00:00Z"
  }
]
```

**Implementation Checklist**:
- [ ] Verify endpoint exists
- [ ] Order by changedAt DESC (newest first)
- [ ] Include all action types: CREATED, ASSIGNED_TO, STATUS_CHANGED, APPROVED, REJECTED
- [ ] Access control: Assignee, creator, or manager

---

## 🏢 4. DEPARTMENT ENDPOINTS

### 4.1 Get All Departments
**Endpoint**: `GET /api/v1/departments`

**Status**: ✅ LIKELY EXISTS

**Description**: List all departments in business

**Response**:
```json
[
  {
    "id": "uuid",
    "name": "Engineering",
    "headId": "uuid",
    "head": "Head Name",
    "memberCount": 5,
    "createdAt": "2025-01-01T00:00:00Z"
  },
  {
    "id": "uuid",
    "name": "Sales",
    "headId": null,
    "head": null,
    "memberCount": 3,
    "createdAt": "2025-02-01T00:00:00Z"
  }
]
```

**Implementation Checklist**:
- [ ] Verify endpoint exists
- [ ] Include member count aggregation
- [ ] Order by name ASC
- [ ] Access control: All authenticated users

---

### 4.2 Create Department
**Endpoint**: `POST /api/v1/departments`

**Status**: ❌ CHECK & POSSIBLY CREATE

**Description**: Create new department

**Request Body**:
```json
{
  "name": "Quality Assurance",
  "headId": "uuid (optional)"
}
```

**Response**: (201 Created)
```json
{
  "id": "uuid",
  "name": "Quality Assurance",
  "headId": null,
  "memberCount": 0,
  "createdAt": "2026-07-19T16:05:00Z"
}
```

**Implementation Checklist**:
- [ ] Verify endpoint exists or create
- [ ] Validate department name not empty
- [ ] Check name uniqueness in business
- [ ] If headId provided, verify user exists in business
- [ ] Access control: CEO and MANAGER only
- [ ] Return 400 if duplicate name
- [ ] Return 404 if head not found

**Location**: `backend/src/main/java/com/contextcraft/portal/controller/DepartmentController.java`

---

### 4.3 Update Department
**Endpoint**: `PUT /api/v1/departments/{departmentId}`

**Status**: ❌ CHECK & POSSIBLY CREATE

**Description**: Update department name or head

**Path Parameters**:
- `departmentId` (required): UUID of department

**Request Body**:
```json
{
  "name": "Quality Assurance (Updated)",
  "headId": "uuid or null"
}
```

**Response**: (200 OK)
```json
{
  "id": "uuid",
  "name": "Quality Assurance (Updated)",
  "headId": "uuid",
  "head": "Head Name",
  "memberCount": 2,
  "updatedAt": "2026-07-19T16:10:00Z"
}
```

**Implementation Checklist**:
- [ ] Verify endpoint exists or create
- [ ] Validate name uniqueness (excluding self)
- [ ] If headId provided, verify user exists and is in business
- [ ] Access control: CEO and MANAGER only
- [ ] Return 404 if department not found

**Location**: `backend/src/main/java/com/contextcraft/portal/controller/DepartmentController.java`

---

## 👔 5. ROLE & PERMISSION ENDPOINTS

### 5.1 Get All Roles
**Endpoint**: `GET /api/v1/roles`

**Status**: ✅ LIKELY EXISTS

**Description**: List all available roles in business

**Response**:
```json
[
  {
    "id": "uuid",
    "name": "CEO",
    "permissions": [
      "TASK_CREATE", "TASK_EDIT", "TASK_DELETE", "TASK_APPROVE",
      "USER_INVITE", "USER_EDIT", "USER_DELETE", "USER_SUSPEND",
      "DEPARTMENT_CREATE", "DEPARTMENT_EDIT", "DEPARTMENT_DELETE",
      "ROLE_CREATE", "ROLE_EDIT", "ROLE_DELETE",
      "ANALYTICS_VIEW", "SETTINGS_EDIT"
    ],
    "memberCount": 1,
    "isBuiltIn": true,
    "canDelete": false
  },
  {
    "id": "uuid",
    "name": "Developer",
    "permissions": ["TASK_VIEW", "TASK_EDIT_OWN", "ATTACHMENT_UPLOAD"],
    "memberCount": 5,
    "isBuiltIn": false,
    "canDelete": true
  }
]
```

**Implementation Checklist**:
- [ ] Verify endpoint exists
- [ ] Include all permissions for each role
- [ ] Mark built-in roles (CEO, Manager, Employee) with `isBuiltIn=true`
- [ ] Set `canDelete` based on whether role is built-in and has members
- [ ] Access control: All authenticated users

---

### 5.2 Create Custom Role
**Endpoint**: `POST /api/v1/roles`

**Status**: ❌ CHECK & POSSIBLY CREATE

**Description**: Create new custom role

**Request Body**:
```json
{
  "name": "Senior Auditor",
  "permissions": ["TASK_CREATE", "TASK_EDIT", "TASK_VIEW", "ANALYTICS_VIEW"]
}
```

**Response**: (201 Created)
```json
{
  "id": "uuid",
  "name": "Senior Auditor",
  "permissions": ["TASK_CREATE", "TASK_EDIT", "TASK_VIEW", "ANALYTICS_VIEW"],
  "memberCount": 0,
  "isBuiltIn": false,
  "createdAt": "2026-07-19T16:15:00Z"
}
```

**Implementation Checklist**:
- [ ] Verify endpoint exists or create
- [ ] Validate role name not empty
- [ ] Check name uniqueness in business
- [ ] Validate all permission names exist
- [ ] Prevent creating roles with names matching built-ins (CEO, Manager, Employee)
- [ ] Access control: CEO only
- [ ] Return 400 if invalid permissions

**Location**: `backend/src/main/java/com/contextcraft/portal/controller/RoleController.java`

---

### 5.3 Add Permission to Role
**Endpoint**: `POST /api/v1/roles/{roleId}/permissions`

**Status**: ❌ CHECK & POSSIBLY CREATE

**Description**: Add a permission to existing role

**Path Parameters**:
- `roleId` (required): UUID of role

**Request Body**:
```json
{
  "permission": "ANALYTICS_VIEW"
}
```

**Response**: (200 OK)
```json
{
  "id": "uuid",
  "name": "Senior Auditor",
  "permissions": ["TASK_CREATE", "TASK_EDIT", "TASK_VIEW", "ANALYTICS_VIEW", "ANALYTICS_EXPORT"],
  "updatedAt": "2026-07-19T16:20:00Z"
}
```

**Implementation Checklist**:
- [ ] Verify endpoint exists or create
- [ ] Validate permission name is valid
- [ ] Prevent adding duplicate permission
- [ ] Prevent modifying built-in roles
- [ ] Create RolePermission record
- [ ] Access control: CEO only
- [ ] Return 404 if role not found
- [ ] Return 400 if invalid permission

**Location**: `backend/src/main/java/com/contextcraft/portal/controller/RoleController.java`

---

### 5.4 Remove Permission from Role
**Endpoint**: `DELETE /api/v1/roles/{roleId}/permissions/{permission}`

**Status**: ❌ CHECK & POSSIBLY CREATE

**Description**: Remove permission from role

**Path Parameters**:
- `roleId` (required): UUID of role
- `permission` (required): Permission name (e.g., ANALYTICS_VIEW)

**Response**: (200 OK)
```json
{
  "id": "uuid",
  "name": "Senior Auditor",
  "permissions": ["TASK_CREATE", "TASK_EDIT", "TASK_VIEW", "ANALYTICS_VIEW"],
  "updatedAt": "2026-07-19T16:25:00Z"
}
```

**Implementation Checklist**:
- [ ] Verify endpoint exists or create
- [ ] Delete RolePermission record
- [ ] Prevent removing permissions that would make role invalid (e.g., CEO must have all)
- [ ] Prevent modifying built-in roles
- [ ] Access control: CEO only
- [ ] Return 404 if role or permission not found

**Location**: `backend/src/main/java/com/contextcraft/portal/controller/RoleController.java`

---

## 🤖 6. TELEGRAM BOT STATUS ENDPOINT

### 6.1 Get Telegram Webhook Status
**Endpoint**: `GET /api/v1/telegram/status`

**Status**: ⚠️ CHECK & ENHANCE

**Description**: Get Telegram bot webhook configuration and status

**Response**:
```json
{
  "isActive": true,
  "webhookUrl": "https://agentcraft.example.com/api/v1/telegram/webhook",
  "botUsername": "agent_craft_bot",
  "botId": 8841120098,
  "lastUpdate": "2026-07-19T14:30:00Z",
  "lastActivity": "2026-07-19T15:45:00Z",
  "messagesSinceUpdate": 42,
  "isSecureConnection": true,
  "certificateExpiresAt": "2027-07-19T00:00:00Z"
}
```

**Implementation Checklist**:
- [ ] Verify endpoint exists
- [ ] Query Telegram bot status API
- [ ] Calculate time since last webhook update
- [ ] Check certificate validity if using custom domain
- [ ] Cache for 5 minutes
- [ ] Access control: Any authenticated user (own business only)

**Location**: `backend/src/main/java/com/contextcraft/portal/controller/` (or TelegramMiniAppController)

---

## 🔒 7. ROLE-BASED ACCESS CONTROL (RBAC) — Audit All Endpoints

### 7.1 Permission Matrix

**Built-in Roles and Their Permissions**:

| Permission | CEO | Manager | Lead | Employee | Guest |
|---|---|---|---|---|---|
| TASK_CREATE | ✅ | ✅ | ✅ | ❌ | ❌ |
| TASK_VIEW_ALL | ✅ | ✅ | ✅ | ❌ | ❌ |
| TASK_VIEW_OWN | ✅ | ✅ | ✅ | ✅ | ❌ |
| TASK_EDIT_OWN | ✅ | ✅ | ✅ | ✅ | ❌ |
| TASK_EDIT_ANY | ✅ | ✅ | ❌ | ❌ | ❌ |
| TASK_DELETE | ✅ | ✅ | ❌ | ❌ | ❌ |
| TASK_APPROVE | ✅ | ✅ | ✅ | ❌ | ❌ |
| USER_INVITE | ✅ | ✅ | ❌ | ❌ | ❌ |
| USER_EDIT | ✅ | ✅ | ❌ | ❌ | ❌ |
| USER_DELETE | ✅ | ❌ | ❌ | ❌ | ❌ |
| USER_SUSPEND | ✅ | ✅ | ❌ | ❌ | ❌ |
| DEPARTMENT_CREATE | ✅ | ✅ | ❌ | ❌ | ❌ |
| DEPARTMENT_EDIT | ✅ | ✅ | ❌ | ❌ | ❌ |
| DEPARTMENT_DELETE | ✅ | ❌ | ❌ | ❌ | ❌ |
| ROLE_CREATE | ✅ | ❌ | ❌ | ❌ | ❌ |
| ROLE_EDIT | ✅ | ❌ | ❌ | ❌ | ❌ |
| ROLE_DELETE | ✅ | ❌ | ❌ | ❌ | ❌ |
| ANALYTICS_VIEW | ✅ | ✅ | ✅ | ❌ | ❌ |
| ANALYTICS_EXPORT | ✅ | ✅ | ❌ | ❌ | ❌ |
| SETTINGS_VIEW | ✅ | ✅ | ❌ | ❌ | ❌ |
| SETTINGS_EDIT | ✅ | ❌ | ❌ | ❌ | ❌ |

### 7.2 Implementation Checklist for RBAC

For EACH endpoint above:

- [ ] Verify `@PreAuthorize` or similar annotation exists
- [ ] Check permission evaluation is correct (using PermissionEvaluator)
- [ ] Test with different roles (CEO, Manager, Lead, Employee)
- [ ] Verify 403 FORBIDDEN returned for unauthorized
- [ ] Verify 401 UNAUTHORIZED returned for unauthenticated
- [ ] Verify return values are filtered (don't expose sensitive data to lower roles)

**Recommended Annotation Pattern** (for Spring Security):
```java
@PostMapping
@PreAuthorize("hasPermission(#request, 'TASK_CREATE')")
public ResponseEntity<Task> createTask(@RequestBody TaskRequest request) {
  // Implementation
}
```

---

## ✅ Testing & Validation Checklist

### For Each Endpoint:
- [ ] Happy path: Valid request returns 200/201 with correct response
- [ ] Invalid input: Bad data returns 400 with error message
- [ ] Not found: Non-existent resource returns 404
- [ ] Unauthorized: No token returns 401
- [ ] Forbidden: Insufficient permissions returns 403
- [ ] Conflict: Duplicate resource returns 409 (where applicable)
- [ ] Response includes all documented fields
- [ ] Response format matches specification (JSON structure)
- [ ] Timestamp fields use ISO 8601 format
- [ ] Numeric IDs are UUIDs (not sequential integers)

### Integration Testing:
- [ ] Create user → Invite user → User accepts → Can login
- [ ] Create task → Assign to user → User updates status → Manager approves
- [ ] User suspension prevents login
- [ ] Role change affects permissions immediately
- [ ] Department deletion (if cascade logic exists) handles task/user reassignment

### Performance:
- [ ] List endpoints with pagination perform well (< 1 second for 1000 items)
- [ ] Analytics queries complete within 2 seconds
- [ ] No N+1 query problems in list endpoints
- [ ] Database indexes present on frequently searched columns

---

## 📝 Deployment Verification

Before marking Phase 3 complete:

1. [ ] All endpoints documented in Swagger/OpenAPI (if applicable)
2. [ ] Error responses consistent across all endpoints
3. [ ] No hardcoded URLs or configuration
4. [ ] Logging implemented for audit trail
5. [ ] Rate limiting configured (if needed)
6. [ ] CORS configuration set for frontend origin
7. [ ] Database migrations in place for all new fields/tables
8. [ ] No deprecated endpoints remaining
9. [ ] Health check endpoint available (`GET /health`)
10. [ ] API version included in path (`/api/v1/...`)

---

## 🛠️ Command Reference for Testing

```bash
# Test endpoint (replace with actual endpoint)
curl -X GET http://localhost:8080/api/v1/tasks \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json"

# Test with invalid auth (should return 401)
curl -X GET http://localhost:8080/api/v1/tasks

# Test with insufficient permissions (should return 403)
curl -X POST http://localhost:8080/api/v1/users/invite \
  -H "Authorization: Bearer EMPLOYEE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"email":"new@company.com"}'
```

