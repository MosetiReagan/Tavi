# Contributing to Tavi

Thank you for your interest in contributing to **Tavi**! Tavi is built as a privacy-first, fully on-device personal AI assistant for Android, running instruction-tuned LLMs (Gemma 3 1B) locally on mobile hardware with native system integration.

We welcome contributions ranging from new native Android tools and runtime optimizations to bug fixes and documentation improvements.

---

## Code of Conduct

We are committed to providing a friendly, safe, and welcoming environment for all contributors. Please treat everyone with respect and kindness.

---

## Architectural Principles

When writing or reviewing code for Tavi, adhere strictly to these core principles:

1. **Zero Cloud Leaks:** Tavi must never send private user data, conversation history, screen content, or memory entries to remote servers. All conversational inference, memory storage, and tool planning must function completely offline.
2. **Consequential Tool Gating:** Any tool that modifies external state or performs irreversible operations (e.g. sending SMS, deleting calendar events, clearing persistent memory) **must** declare `isConsequential = true` and undergo human-in-the-loop confirmation. The engine strips any synthetic or prompt-injected confirmation flags at the execution boundary.
3. **Data Sanitization & Injection Defense:** All external content (SMS, notifications, web pages, clipboard) must be passed through `SecurityGuard.sanitizeExternalContent()` to neutralize prompt injection markers.
4. **Hardware & Token Budgets:** Tavi is optimized for resource-constrained Android devices (4 GB RAM target, e.g. Samsung Galaxy A14). Keep memory allocations lean, avoid holding large bitmaps, and respect the 2,048-token context budget managed by `ContextBudgetManager`.

---

## Development Setup

### Prerequisites
- **Android Studio Ladybug (2024.2.1+)** or higher
- **Android SDK:** API 35 (Android 15) compile SDK, Minimum API 26 (Android 8.0)
- **JDK:** Java 17+
- **Physical Device or Emulator:** An arm64-v8a device with >= 4 GB RAM is recommended for running on-device Gemma inference tests.

### Building
```bash
# Clone the repository
git clone https://github.com/MosetiReagan/tavi.git
cd tavi

# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest
```

---

## Implementing a New Native Tool

All tools extend the `AssistantTool` abstract class located at `app/src/main/java/com/example/tavi/tools/AssistantTool.kt`:

```kotlin
class MyCustomTool(private val context: Context) : AssistantTool {
    override val name: String = "my_custom_tool"
    override val description: String = "Brief explanation of what the tool does and when to use it."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(name = "param_key", type = "string", description = "Parameter description", required = true)
    )
    override val requiredPermissions: List<String> = listOf()
    override val isConsequential: Boolean = false

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        // 1. Validate inputs
        val param = arguments["param_key"] as? String ?: return ToolResult.failure("Missing required parameter")
        
        // 2. Execute local Android API safely
        return try {
            val result = doWork(param)
            ToolResult.success(result)
        } catch (e: Exception) {
            ToolResult.failure(e.message ?: "Execution failed")
        }
    }
}
```

Register your new tool in `ToolRegistry.kt` and add unit tests under `app/src/test/java/com/example/tavi/tools/`.

---

## Pull Request Guidelines

1. **Branch Naming:** Use descriptive branch names:
   - `feat/new-tool-name`
   - `fix/memory-leak-fix`
   - `docs/update-guide`
2. **Atomic Commits:** Follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`).
3. **Test Coverage:** Ensure `./gradlew testDebugUnitTest` passes cleanly before submitting.
4. **No Binary Model Weights in Git:** Never commit `.task`, `.litertlm`, `.bin`, or large model weight files directly to git.

---

## License

By contributing to Tavi, you agree that your contributions will be licensed under the [Apache License, Version 2.0](LICENSE).
