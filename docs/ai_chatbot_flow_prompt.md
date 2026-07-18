# AgentCraft / FlowZint — Universal Conversational AI Agent Prompt (100% Portal Actions)

> **Instructions for AI Coding Assistant / Developer:**
> This prompt defines the architecture for a **Universal Conversational AI Agent** (Gemini / Claude style flow) that governs **EVERY SINGLE ACTION AND DOMAIN** across the entire Company Portal. 
> 
> **CRITICAL ARCHITECTURAL DIRECTIVE:**
> 1. Do **NOT** use hardcoded regex, string matchers (`split(" ")`, `startsWith(...)`), or domain-specific hardcoded state machines.
> 2. **ALL** user messages in Telegram or Chat must pass through a unified **LLM Function Calling & Intent Processing Engine** covering **100% of Portal Capabilities**.
> 3. For **ANY data-modifying action** (Create, Update, Delete, Assign, Approve, Reject, Suspend, Invite, Modify Settings) across **ANY domain**, the AI must summarize the proposed changes and **ASK ONCE FOR CONFIRMATION** before executing on the database.
> 4. For **read-only queries** (Reports, Status, Lists, Analytics), the AI fetches live portal data and responds immediately with natural conversational answers.

---

## 🌐 UNIVERSAL PORTAL ACTION COVERAGE MATRIX

The AI Agent must handle natural language requests for **all** of the following portal capabilities:

### 1. 📋 Task Management Domain
- **Create Task(s) (Single or Bulk):** `"Create a task for audit and assign to Rahul"` / `"Create 3 tasks: ..."`
- **Assign / Reassign Task:** `"Reassign task #4 to Jessica"` / `"Assign all pending QA tasks to Alex"`
- **Update Task Status:** `"Mark inventory audit as completed"` / `"Move frontend redesign to In Progress"`
- **Approve / Reject Task:** `"Approve task #12"` / `"Reject task #15 because of missing docs"`
- **Task Priority & Deadline:** `"Set task #8 priority to HIGH and deadline to Friday EOD"`
- **Task Search & Filter (Read Query):** `"Show all overdue tasks in UI/UX department"`

### 2. 👥 Employee & Team Management Domain
- **Invite Employee(s) (Single or Bulk):** `"Add 3 employees: Aarya (Backend), Anushka (UI/UX), Akshay (Lead)"`
- **Role Assignment & Changes:** `"Change Jessica's role from Developer to Lead"`
- **Department Assignment & Transfers:** `"Move Rahul from Sales to Marketing department"`
- **User Suspension & Activation:** `"Suspend user John due to leave"` / `"Reactivate user Sarah"`
- **Employee Search & Directory (Read Query):** `"Who is in the Engineering team?"`

### 3. 🏢 Department & Role Domain
- **Create Department:** `"Create a new department called Quality Assurance"`
- **Modify / Rename Department:** `"Rename Sales department to Enterprise Growth"`
- **Create Custom Role:** `"Create a new role called Senior Auditor with task creation permissions"`
- **Assign Department Head:** `"Set Akshay as the head of Management department"`

### 4. 📊 Analytics, Metrics & Reports Domain (Read-Only)
- **KPI & Performance Summaries:** `"Show me this week's task completion rate"`
- **Missed Deadlines & Bottlenecks:** `"Which department has the most overdue tasks?"`
- **Generate / Export Reports:** `"Export monthly performance report for Sales"`

### 5. ⚙️ Business Settings & Portal Identity Domain
- **Business Profile Updates:** `"Update business name to ContextCraft AI"`
- **Working Hours & Timezones:** `"Set working hours from 9 AM to 6 PM EST"`
- **Telegram Bot Settings:** `"Disconnect / reconnect webhook"`

---

## 🔄 UNIVERSAL AI EXECUTION & CONFIRMATION PROTOCOL

```mermaid
flowchart TD
    A[User sends freeform natural language message] --> B[LLM Intent & Entity Engine]
    B --> C{What type of action is requested?}
    
    C -- Read Query (Search, Stats, Lists) --> D[Query PostgreSQL / Redis DB]
    D --> E[Return conversational AI response directly]
    
    C -- Data-Modifying Action (Create/Update/Delete/Assign/Approve/Suspend) --> F{Are all required fields present?}
    
    F -- Missing mandatory details --> G[Ask natural clarifying question (Doubt Resolution)]
    G --> A
    
    F -- Complete details present --> H[Generate Structured Pending Action Plan]
    H --> I[Store Action Plan in Redis (pending_action:chatId, TTL 5m)]
    I --> J[Send natural confirmation prompt: 'I am ready to perform X. Shall I proceed?']
    
    J --> K[User responds with confirmation: 'Yes', 'Proceed', 'Go ahead']
    K --> L[Retrieve Action Plan from Redis & execute Spring Boot DB Service]
    L --> M[Send natural completion confirmation to user]
```

---

## 💬 DIALOGUE EXAMPLES FOR ALL PORTAL DOMAINS

### Example 1: Task Reassignment & Priority Update (Task Domain)
- **User:** `"Can you move task #14 to High priority and reassign it to Jessica Taylor in UI/UX?"`
- **AI Response (Pre-Execution Confirmation):**
  > I've set up the following updates for **Task #14 (Frontend Redesign)**:
  > • **New Assignee:** Jessica Taylor (*UI/UX Department*)
  > • **Priority:** High
  > 
  > Shall I go ahead and apply these changes to the portal?
- **User:** `"yes go ahead"`
- **AI Response (Executed):**
  > ✅ Task #14 has been updated. Jessica Taylor is now assigned and its priority is set to High.

### Example 2: Approving & Rejecting Tasks (Approval Workflow Domain)
- **User:** `"Approve task #22 and reject task #23 because the budget report is incomplete"`
- **AI Response (Pre-Execution Confirmation):**
  > I'm ready to process these task approvals:
  > 1. **Task #22:** ✅ Approve & mark completed
  > 2. **Task #23:** ❌ Reject with reason: *"Budget report is incomplete"*
  > 
  > Would you like me to execute these actions on the portal?
- **User:** `"yep proceed"`
- **AI Response (Executed):**
  > ✅ Task #22 has been approved and Task #23 has been rejected with your note.

### Example 3: Changing Employee Role & Department (User Domain)
- **User:** `"Promote Akshay to Team Lead and move him to the Product Management department"`
- **AI Response (Pre-Execution Confirmation):**
  > I've prepared the following changes for **Akshay**:
  > • **Role:** Lead
  > • **Department:** Product Management
  > 
  > Should I update Akshay's profile on your company portal?
- **User:** `"do it"`
- **AI Response (Executed):**
  > ✅ Done! Akshay is now set as Lead in Product Management.

### Example 4: Creating a Department (Department Domain)
- **User:** `"Set up a new department called DevOps"`
- **AI Response (Pre-Execution Confirmation):**
  > I am ready to create the **DevOps** department on your company portal.
  > 
  > Would you like me to proceed?
- **User:** `"sure"`
- **AI Response (Executed):**
  > ✅ The **DevOps** department has been successfully created.

### Example 5: Read-Only Query (Analytics & Status Domain - NO confirmation step needed)
- **User:** `"Show me all pending tasks in the DevOps department"`
- **AI Response (Immediate Execution):**
  > Here are the pending tasks for **DevOps**:
  > • **Task #31:** CI/CD Pipeline Setup (*Assigned to Aarya*)
  > • **Task #34:** Docker Registry Security Audit (*Unassigned*)

---

## 🎯 SYSTEM IMPLEMENTATION CHECKLIST FOR DEVELOPERS

1. **Unified Spring AI / LLM Controller:** `AiAgentService.java` routes all inbound messages to an LLM prompt containing tool definitions for ALL Spring Boot services (`TaskService`, `UserService`, `DepartmentService`, `RoleService`, `AnalyticsService`, `BusinessService`).
2. **Universal Redis Pending Action Store:** Store any data-modifying function payload under key `pending_action:<chat_id>` in Redis with 300-second TTL.
3. **Intent Categorization:**
   - **MUTATION Intents** (Create, Update, Delete, Reassign, Approve, Suspend, Invite, Settings Change) -> **Trigger Confirmation Step ("Ask Once")**.
   - **QUERY Intents** (List, Search, Metrics, Summarize, Help) -> **Execute & Respond Immediately**.
4. **Natural Affirmation Matcher:** Handle conversational user confirmations (`"yes"`, `"yeah"`, `"confirm"`, `"do it"`, `"go ahead"`, `"sure"`, `"proceed"`, `"ok"`, `"approved"`).
