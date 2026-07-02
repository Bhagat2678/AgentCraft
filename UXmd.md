
# BizPortal WhatsApp Conversation Specification

## Purpose
This document defines the conversation flow between the WhatsApp chatbot and each role in the organization.
It is intended to help backend developers implement the conversation state machine (FSM).

---

# Conversation Rules

1. Detect user role using the registered phone number.
2. Display only the menu for that role.
3. Each answer transitions to the next conversation state.
4. Validate all user inputs before saving.
5. Return to the role's main menu after a completed operation unless the workflow continues.
6. Use:
   - Reply Buttons for Yes / No
   - List Messages for menu selections
   - Text Messages for free text
   - Template Messages for notifications

---

# CEO

| Question ID | Module | Bot Question | Expected User Response | Final Bot Response |
|------------|--------|--------------|------------------------|--------------------|
| CEO-Q001 | Welcome | Welcome to BizPortal. What would you like to do? | Create Portal / Access Existing | Opening selected option... |
| CEO-Q002 | Company | Please enter your company name. | Company Name | Company name saved. What is your business type? |
| CEO-Q003 | Business Type | Select your business type. | Retail / Services / Manufacturing | Business type saved. |
| CEO-Q004 | Owner | Enter the owner's name. | Name | Owner information saved. |
| CEO-Q005 | Description | Enter a company description. | Free Text | Description saved. |
| CEO-Q006 | Workflow | Describe your business workflow or choose a template. | Template / Text | Workflow configured successfully. |
| CEO-Q007 | Partner | Enter partner phone number. | 10-digit number | Partner added successfully. |
| CEO-Q008 | Manager | Enter manager phone number. | 10-digit number | Invitation sent to manager. |
| CEO-Q009 | Managers | Select a manager to view. | Manager Name | Manager details displayed. |
| CEO-Q010 | Employees | Enter employee ID or phone number. | ID / Phone | Employee profile displayed. |
| CEO-Q011 | Broadcast | Enter broadcast message. | Text | Broadcast sent successfully. |
| CEO-Q012 | Exit | Exit chatbot? | Yes | Session ended. |

---

# Manager

| Question ID | Module | Bot Question | Expected User Response | Final Bot Response |
|------------|--------|--------------|------------------------|--------------------|
| MGR-Q001 | Overview | View team progress? | Open | Analytics displayed. |
| MGR-Q002 | Team | Select an employee. | Employee | Employee details displayed. |
| MGR-Q003 | Assign Task | Enter task title. | Task Title | Description required. |
| MGR-Q004 | Priority | Select priority. | High / Medium / Low | Priority saved. |
| MGR-Q005 | Deadline | Enter deadline. | Date | Deadline saved. |
| MGR-Q006 | Review | Approve or Reject? | Approve / Reject | Task updated successfully. |
| MGR-Q007 | Messages | Choose recipient. | Employee / Team / CEO | Message sent. |
| MGR-Q008 | Exit | Exit chatbot? | Yes | Session ended. |

---

# Lead

| Question ID | Module | Bot Question | Expected User Response | Final Bot Response |
|------------|--------|--------------|------------------------|--------------------|
| LEAD-Q001 | Team | Select a team member. | Employee | Employee selected. |
| LEAD-Q002 | Assign Work | Enter work details. | Free Text | Work assigned successfully. |
| LEAD-Q003 | Progress | View team progress? | Open | Progress displayed. |
| LEAD-Q004 | Messages | Enter your message. | Text | Message sent successfully. |
| LEAD-Q005 | Exit | Exit chatbot? | Yes | Session ended. |

---

# Employee

| Question ID | Module | Bot Question | Expected User Response | Final Bot Response |
|------------|--------|--------------|------------------------|--------------------|
| EMP-Q001 | Tasks | View pending tasks? | Open | Task list displayed. |
| EMP-Q002 | Task Details | Select a task. | Task | Task details displayed. |
| EMP-Q003 | Clarification | Need clarification? | Question | Manager notified. |
| EMP-Q004 | Submit Work | Upload proof. | Document / Image | Submission received successfully. |
| EMP-Q005 | Complete | Mark task complete? | Yes | Awaiting manager approval. |
| EMP-Q006 | Messages | Choose recipient. | Lead / Manager / CEO | Message sent successfully. |
| EMP-Q007 | Exit | Exit chatbot? | Yes | Session ended. |

---

# Backend Notes

## State Flow

Login
→ Detect Role
→ Display Role Menu
→ Ask Question
→ Validate Input
→ Save Data
→ Send Confirmation
→ Next Question / Return to Menu

## Validation

- Phone number must be valid.
- Buttons must match available options.
- Required fields cannot be empty.
- Invalid input should prompt the user to retry.

## Future Expansion

- Add Department module.
- Add Analytics module.
- Add Notifications.
- Add Leave Management.
- Add Attendance.
- Add Approval workflows.
- Add Reports & Exports.
