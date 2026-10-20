# ADR 001: Model Selection and On-Device Inference Runtime for Tavi

## Status
**Decided and Accepted**

## Context
Tavi is a fully on-device, local-first personal AI assistant for Android. The assistant must:
1. Run language-model inference entirely on-device without cloud API dependencies.
2. Function completely offline once initial model weights are downloaded.
3. Access permitted phone data (Calendar, Notifications, Messages) and perform actions reliably.
4. Fit within memory and compute budgets of ordinary Android devices (3GB–8GB RAM).
5. Support a typed, permission-controlled tool registry where the assistant proposes structured actions that the application verifies and executes.

## Candidate Evaluation

### 1. Gemma 3n E2B
- **Architecture**: Next-generation multimodal on-device model from Google.
- **Memory footprint**: Effective 2B parameters (~1.6GB–2.2GB int4 weights, ~3.8GB–4.8GB peak RAM including KV cache and multimodal embedders).
- **Runtime support**: Google LiteRT-LM / MediaPipe GenAI (requires emerging LiteRT 0.10.22+ and Vulkan/GPU delegates).
- **Assessment**: Excellent capability, but higher RAM requirement exceeds the safe threshold for memory-constrained 4GB Android devices. Relegated to an optional high-tier download profile for devices with $\ge 8\text{GB}$ RAM.

### 2. Gemma 3 1B (`litert-community/Gemma3-1B-IT`)
- **Architecture**: Lightweight instruction-tuned text model from Google.
- **Memory footprint**: ~1.1GB in 4-bit quantization (int4), ~1.8GB–2.2GB peak RAM during active inference.
- **Runtime support**: MediaPipe Tasks GenAI (`com.google.mediapipe:tasks-genai`) and LiteRT.
- **License**: Gemma Open Model Terms (permissive with responsible AI provisions).
- **Assessment**: **Selected as Primary Model**. Strikes the optimal balance of instruction-following capability, on-device latency, reasonable memory footprint on standard 4GB+ RAM smartphones, and direct first-class runtime support on Android.

### 3. FunctionGemma 270M
- **Architecture**: Extremely compact model specialized solely for function calling.
- **Memory footprint**: ~200MB int4.
- **Assessment**: **Rejected as Standalone Assistant**. While capable of emitting schema-matching function arguments for pre-defined schemas, its general reasoning, natural conversation, clarifying question generation, and synthesis capabilities are extremely limited. Adopting it would require a dual-model architecture (a general conversational model + FunctionGemma router), which doubles runtime complexity and increases cold-start latency.

### 4. Qwen2.5-0.5B-Instruct & Qwen2.5-1.5B-Instruct
- **Architecture**: High-efficiency instruction-following models by Alibaba.
- **Memory footprint**: Qwen2.5-0.5B requires ~350MB in int4; Qwen2.5-1.5B requires ~1.0GB in int4.
- **Runtime support**: Supported via LiteRT, ONNX Runtime GenAI, and llama.cpp/GGUF.
- **Assessment**: **Selected as Compact Alternative Profile**. Qwen2.5-0.5B-Instruct is supported as an ultra-compact option for memory-constrained hardware (<4GB RAM devices), with identical tool-calling schema compatibility.

## Runtime Architecture Decision

### Modular Inference Engine with Deterministic Action Fast-Path
1. **Primary Local Inference (`LiteRtLlmEngine`)**:
   Uses Google's `tasks-genai` / LiteRT on-device inference API with int4 quantized weights stored in the app's sandboxed storage directory (`context.filesDir/models/`).
2. **Hybrid Deterministic Fallback Engine (`LocalDeterministicEngine`)**:
   Provides immediate zero-latency execution for core tools (date arithmetic, calculator, calendar queries, system reminders, memory lookup) even if the large model file is currently downloading, updating, or temporarily evicted under low-memory pressure (`ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL`).
3. **Structured Tool Calling Protocol**:
   The model emits JSON-formatted tool invocations enclosed in standard markers (e.g. `{"tool": "calendar_create_event", "arguments": {...}}`). The host application parses, validates arguments with Kotlin Serialization, checks runtime Android permissions, requests user confirmation for destructive/consequential actions, executes the action via native Android providers, and passes verified results back.

## Consequences & Safety
- **No Cloud Fallback**: All inference stays strictly on-device.
- **Untrusted External Content**: External content (web pages, messages, notifications) is sanitized and wrapped in strict safety delimitations to prevent prompt injection.
- **User Confirmation**: Consequential actions (sending messages, deleting events, external requests) require explicit user confirmation before execution.
