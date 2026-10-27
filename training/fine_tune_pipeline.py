#!/usr/bin/env python3
"""
Tavi Model Specialization Orchestrator.
Coordinates dataset generation, LoRA fine-tuning, adapter merging, and LiteRT compilation.
"""

import os
import sys
import argparse
import subprocess

def run_command(cmd: str):
    print(f"\n[EXEC] {cmd}\n")
    ret = subprocess.run(cmd, shell=True)
    if ret.returncode != 0:
        print(f"[ERROR] Command failed with exit code {ret.returncode}: {cmd}")
        sys.exit(ret.returncode)

def main():
    parser = argparse.ArgumentParser(description="Tavi Gemma 3 1B Fine-Tuning & LiteRT Compilation Pipeline")
    parser.add_argument("--step", type=str, choices=["dataset", "train", "export", "all"], default="all",
                        help="Pipeline phase to execute.")
    parser.add_argument("--samples", type=int, default=6000, help="Number of synthetic samples to generate.")
    parser.add_argument("--epochs", type=int, default=3, help="Training epochs.")
    parser.add_argument("--batch_size", type=int, default=4, help="Batch size per device.")
    parser.add_argument("--model_id", type=str, default="google/gemma-3-1b-it", help="Base model identifier.")
    parser.add_argument("--output_file", type=str, default="app/src/main/assets/models/gemma-3-1b-it-tavi.litertlm",
                        help="Final LiteRT FlatBuffer path.")
    args = parser.parse_args()

    print("=" * 60)
    print("🚀 TAVI GEMMA 3 1B MODEL SPECIALIZATION PIPELINE")
    print("=" * 60)

    # 1. Dataset Generation
    if args.step in ["dataset", "all"]:
        print("\n--- PHASE 1: Dataset Generation ---")
        run_command(f"python3 training/dataset_generator.py --samples {args.samples} --output_dir training/data")

    # 2. LoRA Fine-Tuning
    if args.step in ["train", "all"]:
        print("\n--- PHASE 2: LoRA Fine-Tuning ---")
        run_command(
            f"python3 training/train_lora.py "
            f"--model_id {args.model_id} "
            f"--train_file training/data/tavi_train.jsonl "
            f"--val_file training/data/tavi_val.jsonl "
            f"--output_dir training/output/gemma-3-1b-tavi-lora "
            f"--epochs {args.epochs} "
            f"--batch_size {args.batch_size}"
        )

    # 3. LiteRT Export
    if args.step in ["export", "all"]:
        print("\n--- PHASE 3: Merge Adapters & Export to LiteRT ---")
        run_command(
            f"python3 training/export_litertlm.py "
            f"--base_model_id {args.model_id} "
            f"--adapter_dir training/output/gemma-3-1b-tavi-lora "
            f"--merged_dir training/output/gemma-3-1b-tavi-merged "
            f"--output_file {args.output_file} "
            f"--quantization int4"
        )

    print("\n" + "=" * 60)
    print("✅ Pipeline completed successfully.")
    print("=" * 60)

if __name__ == "__main__":
    main()
