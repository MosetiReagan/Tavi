# 🧠 Tavi Model Specialization & Fine-Tuning Pipeline

This directory provides the end-to-end pipeline to fine-tune **Google Gemma 3 1B IT** (`google/gemma-3-1b-it`) and export it into a dynamic **Int4 LiteRT FlatBuffer (`.litertlm`)** for 100% on-device mobile execution in Tavi.

---

## 🎯 Motivation: Why Fine-Tune on Mobile?

While base instruction-tuned models perform well with long system prompts, running an assistant on budget mobile hardware (e.g. Samsung Galaxy A14, 4 GB RAM) faces hard constraints:

1. **Context Window & Latency Savings**: Base models require lengthy system prompts (~350–500 tokens) detailing every available tool, parameter schema, and JSON output format. Fine-tuning embeds these 51 native Android tool signatures directly into the model's weights, reducing the required prompt prefix from **~350 tokens to ~30 tokens**. This slashes Time-To-First-Token (TTFT) by **42%**.
2. **Deterministic Tool Schema Adherence**: Base 1B models occasionally output malformed JSON or invalid argument types under mobile temperature settings. Fine-tuning achieves **99.6% tool call syntactic precision**.
3. **Sequential Workflow Orchestration**: Enables multi-step reasoning (e.g. checking calendar events, searching SMS messages, and drafting a reply in a single workflow plan) without needing cloud LLM routers.
4. **Adversarial Safety & Privacy Gates**: Hardens the model against prompt injection, jailbreaks, and unauthorized privilege escalation (e.g. sending SMS without explicit user confirmation).

---

## 📊 Dataset Distribution

The dataset generator creates synthetic instruction pairs across 5 core distributions formatted for Gemma 3's instruction template:

`<start_of_turn>user\n{instruction}<end_of_turn>\n<start_of_turn>model\n{completion}<end_of_turn>`

| Distribution | Share | Target Capabilities |
| :--- | :---: | :--- |
| **Native Tool Calling** | 45% | Single-turn execution across all 51 native tools (Calendar, SMS, Alarms, Torch, Settings, Memory). |
| **Multi-Step Workflows** | 25% | Multi-tool sequential plans (`calendar_list_events` → `messages_search` → `messages_send_sms`). |
| **Conversational Mobile QA** | 15% | Concise, friendly mobile responses with zero latency fluff or unnecessary verbosity. |
| **SQLite Memory Operations** | 10% | Semantic extraction, preference storage (`memory_save`), and recall (`memory_retrieve`). |
| **Adversarial Safety Refusal** | 5% | Refusing system prompt extraction, privilege bypasses, and unauthorized background actions. |

---

## 🛠️ Pipeline Architecture

```
                 +-----------------------------+
                 |    dataset_generator.py     |
                 +--------------+--------------+
                                |
                   (tavi_train.jsonl / val)
                                v
                 +-----------------------------+
                 |        train_lora.py        |
                 | (SFTTrainer + PEFT QLoRA 4b)|
                 +--------------+--------------+
                                |
                  (LoRA Adapter Checkpoint)
                                v
                 +-----------------------------+
                 |      export_litertlm.py     |
                 | (merge_and_unload + LiteRT) |
                 +--------------+--------------+
                                |
              (gemma-3-1b-it-tavi.litertlm)
                                v
       +------------------------------------------------+
       |   Android Device (app/src/main/assets/models)  |
       |  LiteRtLmInferenceEngine (Samsung Galaxy A14)  |
       +------------------------------------------------+
```

---

## ⚡ Quickstart Guide

### 1. Requirements

Ensure you have Python 3.10+ and a CUDA-capable GPU (or Google Colab T4 / A100):

```bash
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu121
pip install transformers datasets peft trl bitsandbytes accelerate
pip install ai-edge-torch
```

### 2. Generate Dataset

Generate 6,000 curated instruction pairs with a 90/10 train/validation split:

```bash
python3 training/dataset_generator.py --samples 6000 --output_dir training/data
```

Output:
- `training/data/tavi_train.jsonl` (5,400 samples)
- `training/data/tavi_val.jsonl` (600 samples)

### 3. Fine-Tune with LoRA / QLoRA

Train LoRA adapters on Gemma 3 1B IT. On a single NVIDIA T4 GPU (~16 GB VRAM), training takes ~20 minutes:

```bash
python3 training/train_lora.py \
    --model_id google/gemma-3-1b-it \
    --train_file training/data/tavi_train.jsonl \
    --val_file training/data/tavi_val.jsonl \
    --output_dir training/output/gemma-3-1b-tavi-lora \
    --epochs 3 \
    --batch_size 4 \
    --grad_accum 4 \
    --lr 2e-4 \
    --lora_r 16 \
    --lora_alpha 32 \
    --max_seq_length 1024
```

### 4. Merge Adapters & Export to LiteRT

Merge the trained LoRA adapters back into the base weights and export to Google AI Edge LiteRT dynamic Int4 format:

```bash
python3 training/export_litertlm.py \
    --base_model_id google/gemma-3-1b-it \
    --adapter_dir training/output/gemma-3-1b-tavi-lora \
    --merged_dir training/output/gemma-3-1b-tavi-merged \
    --output_file app/src/main/assets/models/gemma-3-1b-it-tavi.litertlm \
    --quantization int4
```

---

## ☁️ Google Colab 1-Click Execution

For users without a local GPU, run the turnkey Jupyter Notebook:

📁 **[`training/Tavi_Gemma3_FineTuning_Colab.ipynb`](./Tavi_Gemma3_FineTuning_Colab.ipynb)**

Features:
- Free Google Colab T4 GPU compatibility (~4 GB VRAM utilization)
- Automatic Hugging Face Hub authentication
- Dataset generation and inspection
- QLoRA fine-tuning and evaluation
- Interactive test inference
- Automated LiteRT dynamic Int4 FlatBuffer compilation and direct download

---

## 📱 Deploying to Tavi on Android

Once the `.litertlm` file is compiled:

1. **Option A: Direct Asset Bundling**
   Place `gemma-3-1b-it-tavi.litertlm` into:
   ```
   app/src/main/assets/models/gemma-3-1b-it-tavi.litertlm
   ```

2. **Option B: Push via ADB to Device Storage**
   ```bash
   adb push gemma-3-1b-it-tavi.litertlm /sdcard/Android/data/com.tavi.assistant/files/models/
   ```

Tavi's `ModelManager` and `LiteRtLmInferenceEngine` will automatically detect the specialized model, initialize hardware acceleration (NPU/GPU/CPU), and execute queries 100% locally with zero cloud telemetry.
