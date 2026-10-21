package com.example.tavi.tools.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.tavi.model.ToolCategory
import com.example.tavi.model.ToolExecutionState
import com.example.tavi.model.ToolResult
import com.example.tavi.model.ToolRiskLevel
import com.example.tavi.security.SecurityGuard
import com.example.tavi.tools.AssistantTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

class BrowserResearchTool : AssistantTool {
    override val name: String = "browser_research_topic"
    override val category: ToolCategory = ToolCategory.RESEARCH
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Reads accessible web page content, extracts relevant text, and supports multi-step research. Halts if login or CAPTCHA is encountered."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "query": {"type": "string", "description": "Search query or topic to research"},
            "url": {"type": "string", "description": "Direct URL to read (optional, http/https only)"}
          },
          "required": ["query"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = listOf("android.permission.INTERNET")
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult = withContext(Dispatchers.IO) {
        val query = arguments["query"]?.toString()?.trim() ?: ""
        var targetUrl = arguments["url"]?.toString()?.trim()

        if (targetUrl.isNullOrEmpty()) {
            // Use safe public search / Wikipedia endpoint for information retrieval
            val encoded = URLEncoder.encode(query, "UTF-8")
            targetUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&format=json&utf8="
        }

        if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
            return@withContext ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "Invalid URL protocol. Only http:// and https:// endpoints are supported."
            )
        }

        try {
            val urlObj = URL(targetUrl)
            val connection = (urlObj.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 12000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile; TaviAssistant/1.0) AppleWebKit/537.36")
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json,*/*")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext ToolResult(
                    toolCallId = toolCallId,
                    toolName = name,
                    resultJson = "{}",
                    isSuccess = false,
                    errorMessage = "HTTP request returned error code $responseCode from $targetUrl."
                )
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val rawBuffer = StringBuilder()
            var line: String?
            var bytesRead = 0
            val maxBytes = 60000

            while (reader.readLine().also { line = it } != null) {
                rawBuffer.append(line).append("\n")
                bytesRead += (line?.length ?: 0)
                if (bytesRead > maxBytes) break
            }
            reader.close()
            connection.disconnect()

            val rawHtml = rawBuffer.toString()

            // Check for login, CAPTCHA, or authentication walls
            val requiresLogin = Pattern.compile("(?i)(captcha|recaptcha|cf-turnstile|sign in to continue|log in to continue|access denied)").matcher(rawHtml).find()
            if (requiresLogin) {
                val resultObj = JSONObject().apply {
                    put("status", "USER_TAKEOVER_REQUIRED")
                    put("url", targetUrl)
                    put("reason", "The requested page requires authentication, CAPTCHA resolution, or account login. Tavi cannot and does not bypass website security controls. Please open the link in your browser to proceed.")
                }
                return@withContext ToolResult(
                    toolCallId = toolCallId,
                    toolName = name,
                    resultJson = resultObj.toString(),
                    isSuccess = true
                )
            }

            // Extract readable text from HTML
            val extractedText = extractReadableText(rawHtml)

            // Crucial: Sanitize external web data against indirect prompt injection
            val sanitized = SecurityGuard.sanitizeExternalContent(extractedText, "Web Page: $targetUrl")

            val resultObj = JSONObject().apply {
                put("status", "SUCCESS")
                put("url", targetUrl)
                put("query", query)
                put("extractedLength", extractedText.length)
                put("content", sanitized)
                put("checkpoint", "RESEARCH_STEP_COMPLETE")
            }

            return@withContext ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = resultObj.toString(),
                isSuccess = true
            )

        } catch (e: Exception) {
            return@withContext ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "Browser task network failure: ${e.message}"
            )
        }
    }

    private fun extractReadableText(html: String): String {
        var clean = html
        // Remove script and style tags
        clean = Pattern.compile("(?s)<script.*?</script>").matcher(clean).replaceAll("")
        clean = Pattern.compile("(?s)<style.*?</style>").matcher(clean).replaceAll("")
        // Strip tags
        clean = Pattern.compile("<[^>]+>").matcher(clean).replaceAll(" ")
        // Normalize whitespace
        clean = clean.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .lines()
            .map { it.trim() }
            .filter { it.length > 20 } // Filter out short navigation crumbs
            .joinToString("\n")

        return if (clean.length > 3000) clean.substring(0, 3000) else clean
    }
}

class BrowserOpenPageTool : AssistantTool {
    override val name: String = "browser_open_page"
    override val category: ToolCategory = ToolCategory.RESEARCH
    override val riskLevel: ToolRiskLevel = ToolRiskLevel.LOW
    override val description: String = "Opens an accessible URL in the device browser so the user can interact directly."
    override val parametersSchema: String = """
        {
          "type": "object",
          "properties": {
            "url": {"type": "string", "description": "The web URL to open (http/https)"}
          },
          "required": ["url"]
        }
    """.trimIndent()
    override val requiredPermissions: List<String> = emptyList()
    override val requiresConfirmation: Boolean = false

    override suspend fun execute(
        toolCallId: String,
        arguments: Map<String, Any?>,
        context: Context
    ): ToolResult {
        val url = arguments["url"]?.toString()?.trim()
        if (url.isNullOrEmpty() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "Valid http:// or https:// URL is required."
            )
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)

            val resultObj = JSONObject().apply {
                put("verified", true)
                put("openedUrl", url)
                put("status", "BROWSER_LAUNCHED")
            }

            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = resultObj.toString(),
                isSuccess = true
            )
        } catch (e: Exception) {
            return ToolResult(
                toolCallId = toolCallId,
                toolName = name,
                resultJson = "{}",
                isSuccess = false,
                errorMessage = "Failed to launch browser: ${e.message}"
            )
        }
    }
}
