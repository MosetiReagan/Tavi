package com.example.tavi.tools.contacts

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class ResolvedContact(
    val id: Long,
    val displayName: String,
    val phoneNumber: String,
    val type: String,
    val email: String? = null
)

sealed class ContactResolutionResult {
    data class Single(val contact: ResolvedContact) : ContactResolutionResult()
    data class Ambiguous(val query: String, val candidates: List<ResolvedContact>) : ContactResolutionResult()
    data class NotFound(val query: String) : ContactResolutionResult()
    data class PermissionDenied(val permission: String) : ContactResolutionResult()
}

object ContactResolver {

    fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Searches device contacts matching the given query string across names, phones, and emails.
     * Supports multi-word matching, fuzzy whitespace normalization, and token overlap ranking.
     */
    fun searchContacts(context: Context, query: String, limit: Int = 10): List<ResolvedContact> {
        if (!hasContactsPermission(context)) return emptyList()

        val results = mutableListOf<ResolvedContact>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE
        )
        val trimmedQuery = query.trim()
        val firstWord = trimmedQuery.split(Regex("\\s+")).firstOrNull() ?: trimmedQuery
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        val selectionArgs = arrayOf("%$trimmedQuery%", "%$firstWord%", "%$trimmedQuery%")
        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)

                val candidates = mutableListOf<ResolvedContact>()
                while (cursor.moveToNext()) {
                    val id = if (idIndex != -1) cursor.getLong(idIndex) else -1L
                    val name = if (nameIndex != -1) cursor.getString(nameIndex) ?: "" else ""
                    val num = if (numIndex != -1) cursor.getString(numIndex) ?: "" else ""
                    val typeInt = if (typeIndex != -1) cursor.getInt(typeIndex) else -1
                    val type = try {
                        ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                            context.resources,
                            typeInt,
                            "Mobile"
                        ).toString()
                    } catch (_: Exception) {
                        "Mobile"
                    }

                    if (name.isNotEmpty() && num.isNotEmpty()) {
                        candidates.add(ResolvedContact(id, name, num, type))
                    }
                }

                // Rank candidates: exact normalized matches first, then prefix, then containment
                val normalizedQuery = trimmedQuery.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
                val sorted = candidates.sortedByDescending { c ->
                    val normName = c.displayName.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
                    val normNum = c.phoneNumber.replace(Regex("[^0-9]"), "")
                    when {
                        normName == normalizedQuery || normNum == normalizedQuery -> 100
                        normName.startsWith(normalizedQuery) -> 80
                        normName.contains(normalizedQuery) -> 60
                        else -> 10
                    }
                }

                val seenIds = mutableSetOf<String>()
                for (c in sorted) {
                    val key = "${c.displayName}_${c.phoneNumber}"
                    if (!seenIds.contains(key)) {
                        seenIds.add(key)
                        results.add(c)
                        if (results.size >= limit) break
                    }
                }
            }
        } catch (_: Exception) {}
        return results
    }

    /**
     * Resolves a recipient string (name or number) to a normalized ContactResolutionResult.
     * Exposes ambiguity instead of guessing.
     */
    fun resolveContactStrict(context: Context, nameOrNumber: String): ContactResolutionResult {
        val trimmed = nameOrNumber.replace(Regex("(?i)^to\\s+"), "").trim()
        val digitsOnly = trimmed.replace(Regex("[\\s\\-\\+\\(\\)]"), "")
        val isNumeric = digitsOnly.isNotEmpty() && digitsOnly.all { it.isDigit() } && digitsOnly.length >= 4

        // If it's already a numeric phone number, return direct single contact
        if (isNumeric) {
            return ContactResolutionResult.Single(
                ResolvedContact(id = -1, displayName = trimmed, phoneNumber = trimmed, type = "Direct")
            )
        }

        if (!hasContactsPermission(context)) {
            return ContactResolutionResult.PermissionDenied(Manifest.permission.READ_CONTACTS)
        }

        val matches = searchContacts(context, trimmed, limit = 10)
        if (matches.isEmpty()) {
            return ContactResolutionResult.NotFound(trimmed)
        }

        // Check if there is an exact match
        val exactMatches = matches.filter { it.displayName.equals(trimmed, ignoreCase = true) }
        if (exactMatches.size == 1) {
            return ContactResolutionResult.Single(exactMatches.first())
        } else if (exactMatches.size > 1) {
            return ContactResolutionResult.Ambiguous(trimmed, exactMatches)
        }

        // If only 1 partial match exists, return single
        if (matches.size == 1) {
            return ContactResolutionResult.Single(matches.first())
        }

        // Multiple distinct matches exist -> AMBIGUOUS
        return ContactResolutionResult.Ambiguous(trimmed, matches)
    }

    /**
     * Legacy helper Pair(phoneNumber, displayNameOrNull) for draft creation with graceful fallback.
     */
    fun resolvePhoneNumber(context: Context, nameOrNumber: String): Pair<String, String?> {
        when (val res = resolveContactStrict(context, nameOrNumber)) {
            is ContactResolutionResult.Single -> return Pair(res.contact.phoneNumber, res.contact.displayName)
            is ContactResolutionResult.Ambiguous -> {
                val first = res.candidates.first()
                return Pair(first.phoneNumber, first.displayName)
            }
            else -> return Pair(nameOrNumber.trim(), null)
        }
    }

    /**
     * Creates a new contact in Android ContactsProvider.
     */
    fun createContact(context: Context, name: String, phoneNumber: String, email: String? = null): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return try {
            val ops = ArrayList<ContentProviderOperation>()
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build()
            )
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build()
            )
            if (!email.isNullOrEmpty()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                        .build()
                )
            }
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (_: Exception) {
            false
        }
    }
}
