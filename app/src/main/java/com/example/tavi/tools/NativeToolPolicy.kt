package com.example.tavi.tools

/** Explicit inventory: low risk alone does not mean an operation is safe to retry. */
object NativeToolPolicy {
    val readOnlyTools = setOf(
        "calendar_list_events", "calendar_get_event", "contacts_search", "contacts_resolve",
        "messages_search", "messages_get_send_status", "call_log_search", "notifications_read_summary", "clock_query_time",
        "reminder_list", "app_list_launchable", "device_get_status", "location_get_current",
        "notes_list", "clipboard_read", "memory_retrieve", "calculator", "date_calculation",
        "network_get_status", "usage_get_stats", "accessibility_read_screen", "documents_search"
    )
    fun isReadOnly(name: String) = name in readOnlyTools
}
