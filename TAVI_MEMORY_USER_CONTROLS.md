# Tavi — Phase 4: Persistent Memory User Controls & Operations

**Document Date:** October 26, 2026  
**Auditor:** Systems Architecture & Human-Computer Interaction  
**Target Hardware:** Samsung Galaxy A14 (`SM-A145F`), Android 15 (One UI 7, API 35)  
**Document Path:** `/Users/reagan/Downloads/Tavi/TAVI_MEMORY_USER_CONTROLS.md`  

---

## 1. User Control Principles

Tavi's personal memory system is designed under the principle of **User Sovereignty**:
- The user is the sole owner of their memory store.
- Memories must be inspectable, correctable, and deletable at any time.
- Memory operations must operate on the actual database store, not merely modify ephemeral context.
- Deleting a memory must be clearly distinguished from clearing conversation history.

---

## 2. Conversational Voice & Text Commands

Users can interact with persistent memory directly via natural conversation:

### 2.1 Inspection & Querying
- **Command:** *"What do you remember about me?"* or *"List my saved memories"*
- **Behavior:**
  - Queries `persistent_memories` WHERE `state = 'ACTIVE'`.
  - Assistant formats a concise list of active memories by category (e.g. Preferences, Projects, Entities).

### 2.2 Intentional Saving
- **Command:** *"Remember that my car is a blue Honda CR-V"*
- **Behavior:**
  - Extracts key: `car_model`, value: `Blue Honda CR-V`.
  - Sets provenance: `USER_EXPLICIT`.
  - Saves record to `persistent_memories`.
  - Assistant confirms: *"Saved to memory: car model is Blue Honda CR-V."*

### 2.3 Correcting an Existing Fact
- **Command:** *"Actually, I sold the Honda, my car is now a red Subaru Outback"*
- **Behavior:**
  - Locates existing active record for `car_model`.
  - Marks previous record as `SUPERSEDED`, recording `superseded_by_id`.
  - Inserts new record: `Red Subaru Outback` with state `ACTIVE`.
  - Assistant confirms: *"Updated memory: car model changed from Blue Honda CR-V to Red Subaru Outback."*

### 2.4 Forgetting a Specific Fact
- **Command:** *"Forget my car"* or *"Delete memory about my car"*
- **Behavior:**
  - Locates record matching `car_model`.
  - Soft-deletes record (state set to `DELETED`).
  - Assistant confirms: *"Removed memory about your car model."*

### 2.5 Bulk Memory Deletion
- **Command:** *"Clear all my memories"*
- **Behavior:**
  - Triggers `memory_clear_all`.
  - Because this is a destructive action, Tavi **halts and requires explicit user confirmation**:
    > *"Are you sure you want to permanently clear all persistent memories? This cannot be undone."*
  - Upon user confirmation, all records are deleted.

---

## 3. Settings Screen Integration

In [`SettingsScreen.kt`](file:///Users/reagan/Downloads/Tavi/app/src/main/java/com/example/tavi/ui/settings/SettingsScreen.kt), the user has direct UI controls:

1. **Persistent Memory Master Switch (`isMemoryEnabled`):**
   - When toggled OFF:
     - Zero persistent memories are retrieved or injected into model prompts.
     - New messages do not save or suggest memories.
     - Existing memories remain preserved in the local database until the user explicitly deletes them.
2. **Auto-Suggestions Toggle (`isMemoryAutoSuggestEnabled`):**
   - Controls whether Tavi suggests saving stable facts noticed during task completion.
3. **Memory Management Dialog:**
   - Displays all active memories with timestamps and provenance badges.
   - Provides a delete button per item.
   - Provides a *"Clear All Memories"* button with confirmation dialog.

---

## 4. Memory vs. Conversation History Distinction

| Action | What Is Deleted? | What Remains? |
|:---|:---|:---|
| **Clear Conversation Messages** | Clears `messages` table in SQLite. Current chat screen is wiped. | **Persistent memories remain intact.** The next time the user starts a chat, Tavi still remembers their name, preferences, and projects! |
| **Delete Specific Memory** | Sets memory state to `DELETED`. Memory is excluded from all future prompts. | The chat message where the user first mentioned the fact is still visible in conversation history. |
| **Clear All Memories** | Permanently deletes all records in `persistent_memories` and `episodic_memories`. | Conversation chat messages remain intact unless the user separately clears the chat log. |

---

## 5. Summary

User controls provide complete transparency, instant corrections, safe deletions, and effortless toggling, keeping personal data completely in user hands.
