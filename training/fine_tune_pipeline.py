#!/usr/bin/env python3
"""
Tavi Model Specialization Pipeline:
Fine-tunes base Gemma 3 1B / Qwen2.5 on Tavi tool-calling dataset using PEFT/LoRA,
and converts the resulting weights into a mobile LiteRT / MediaPipe .task bundle.
"""

import os
import json
import argparse

def create_training_spec():
    return {
        "base_model": "google/gemma-3-1b-it",
        "lora_r": 16,
        "lora_alpha": 32,
        "lora_target_modules": ["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
        "learning_rate": 2e-4,
        "batch_size": 4,
        "max_seq_length": 2048,
        "quantization": "int4",
        "target_runtime": "MediaPipe Tasks GenAI / LiteRT"
    }

def print_conversion_steps():
    steps = """
    === Tavi Specialization & Conversion Pipeline ===
    1. Dataset Preparation:
       python -m evaluation.prepare_sft_data --input evaluation/dataset_v1.json --output data/train.jsonl

    2. LoRA Fine-Tuning:
       python -m trl.sft_trainer \
           --model_name_or_path google/gemma-3-1b-it \
           --dataset_name data/train.jsonl \
           --output_dir output/gemma-3-1b-tavi-lora \
           --learning_rate 2e-4 \
           --per_device_train_batch_size 4 \
           --lora_r 16

    3. Merge LoRA Adapters:
       python -m peft.merge_and_unload \
           --base_model google/gemma-3-1b-it \
           --adapter_dir output/gemma-3-1b-tavi-lora \
           --output_dir output/gemma-3-1b-tavi-merged

    4. Quantize and Export to LiteRT .task Bundle:
       ai_edge_torch convert \
           --model output/gemma-3-1b-tavi-merged \
           --quantize int4 \
           --output app/src/main/assets/models/gemma3-1b-it-tavi.task
    """
    print(steps)

if __name__ == "__main__":
    print_conversion_steps()
