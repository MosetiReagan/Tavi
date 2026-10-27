#!/usr/bin/env python3
"""
Tavi Model Export Pipeline:
1. Merges trained LoRA adapter back into base Gemma 3 1B IT weights.
2. Quantizes and compiles into Google AI Edge LiteRT FlatBuffer (.litertlm / .task)
   for zero-cloud, 100% on-device mobile execution in Tavi.

Requirements:
- torch, transformers, peft
- ai-edge-torch or mediapipe
"""

import os
import sys
import shutil
import argparse
import logging
from typing import Optional

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("tavi-export")

def parse_args():
    parser = argparse.ArgumentParser(description="Merge LoRA and export Gemma 3 1B to Google AI Edge LiteRT (.litertlm)")
    parser.add_argument("--base_model_id", type=str, default="google/gemma-3-1b-it", help="Base Hugging Face model identifier.")
    parser.add_argument("--adapter_dir", type=str, default="training/output/gemma-3-1b-tavi-lora", help="Path to LoRA checkpoint directory.")
    parser.add_argument("--merged_dir", type=str, default="training/output/gemma-3-1b-tavi-merged", help="Output directory for merged Hugging Face model.")
    parser.add_argument("--output_file", type=str, default="app/src/main/assets/models/gemma-3-1b-it-tavi.litertlm", help="Output .litertlm or .task file for Android.")
    parser.add_argument("--quantization", type=str, choices=["int4", "int8", "float16"], default="int4", help="LiteRT target quantization format.")
    parser.add_argument("--skip_merge", action="store_true", help="Skip merge step if already merged model exists.")
    return parser.parse_args()

def merge_lora(base_model_id: str, adapter_dir: str, merged_dir: str):
    logger.info("=== Step 1: Merging LoRA Adapter with Base Model ===")
    logger.info(f"Base Model:  {base_model_id}")
    logger.info(f"LoRA Path:   {adapter_dir}")
    logger.info(f"Merged Dest: {merged_dir}")

    try:
        import torch
        from transformers import AutoModelForCausalLM, AutoTokenizer
        from peft import PeftModel
    except ImportError as e:
        logger.error(f"Missing required packages: {e}")
        logger.error("Run: pip install torch transformers peft")
        sys.exit(1)

    if not os.path.exists(adapter_dir):
        logger.error(f"Adapter directory not found: {adapter_dir}")
        sys.exit(1)

    logger.info("Loading base model in full precision (float16/bfloat16) on CPU/GPU...")
    dtype = torch.bfloat16 if torch.cuda.is_available() and torch.cuda.is_bf16_supported() else torch.float16
    base_model = AutoModelForCausalLM.from_pretrained(
        base_model_id,
        torch_dtype=dtype,
        device_map="auto" if torch.cuda.is_available() else "cpu",
        trust_remote_code=True,
    )
    tokenizer = AutoTokenizer.from_pretrained(base_model_id, trust_remote_code=True)

    logger.info("Loading and attaching LoRA weights...")
    model = PeftModel.from_pretrained(base_model, adapter_dir)

    logger.info("Fusing LoRA weights into base model layers (merge_and_unload)...")
    merged_model = model.merge_and_unload()

    logger.info(f"Saving merged standalone model to {merged_dir}...")
    os.makedirs(merged_dir, exist_ok=True)
    merged_model.save_pretrained(merged_dir, safe_serialization=True)
    tokenizer.save_pretrained(merged_dir)
    logger.info("Merged model saved successfully!")

def export_to_litert(merged_dir: str, output_file: str, quantization: str):
    logger.info("=== Step 2: Exporting Merged Weights to LiteRT FlatBuffer ===")
    logger.info(f"Source Model:  {merged_dir}")
    logger.info(f"Output Target: {output_file}")
    logger.info(f"Quantization:  {quantization}")

    os.makedirs(os.path.dirname(os.path.abspath(output_file)), exist_ok=True)

    # Check for ai-edge-torch
    try:
        import ai_edge_torch
        import ai_edge_torch.generative.quantize.quant_recipes as quant_recipes
        from ai_edge_torch.generative.utilities import converter

        logger.info("Detected ai-edge-torch runtime. Converting model...")
        
        # Configure quantization recipe
        recipe = quant_recipes.full_int4_weight_only() if quantization == "int4" else None

        # Execute LiteRT conversion
        converter.convert_hf_to_litert(
            checkpoint_path=merged_dir,
            output_path=output_file,
            quantize_recipe=recipe
        )
        logger.info(f"Successfully generated LiteRT model: {output_file}")
        return

    except ImportError:
        logger.warning("ai_edge_torch not found in current environment.")

    # Fallback to MediaPipe Tasks GenAI CLI tool if present
    mediapipe_converter = shutil.which("mediapipe_genai_converter") or shutil.which("model_converter")
    if mediapipe_converter:
        logger.info(f"Found MediaPipe converter binary: {mediapipe_converter}")
        cmd = f"{mediapipe_converter} --input_ckpt={merged_dir} --output_file={output_file} --quantize={quantization}"
        logger.info(f"Running: {cmd}")
        ret = os.system(cmd)
        if ret == 0:
            logger.info("Conversion completed successfully!")
            return

    logger.info("\n" + "=" * 65)
    logger.info("MANUAL EXPORT INSTRUCTIONS:")
    logger.info("To compile the merged model into LiteRT FlatBuffer on Google Colab / Linux:")
    logger.info("1. Install Google AI Edge:")
    logger.info("   pip install ai-edge-torch")
    logger.info("2. Run the conversion command:")
    logger.info(f"   python -m ai_edge_torch.generative.convert \\")
    logger.info(f"       --checkpoint {merged_dir} \\")
    logger.info(f"       --output {output_file} \\")
    logger.info(f"       --quantize {quantization}")
    logger.info("3. Copy the compiled .litertlm file to:")
    logger.info(f"   {output_file}")
    logger.info("=" * 65 + "\n")

def main():
    args = parse_args()
    if not args.skip_merge:
        merge_lora(args.base_model_id, args.adapter_dir, args.merged_dir)
    else:
        logger.info("Skipping merge step (--skip_merge passed).")

    export_to_litert(args.merged_dir, args.output_file, args.quantization)

if __name__ == "__main__":
    main()
