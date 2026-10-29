#!/usr/bin/env python3
"""
Tavi LoRA Fine-Tuning Script for Google Gemma 3 1B IT.

Optimizes Gemma 3 1B IT for on-device mobile agent execution:
- 51 Native Android Tool Schemas
- Multi-step Sequential Tool Calling & Workflows
- Private SQLite Memory & Profile Store
- Concise Mobile Responses & Low-Latency Generation
- Adversarial Prompt Injection Defense

Uses Hugging Face TRL (SFTTrainer) + PEFT (LoRA / QLoRA 4-bit).
"""

import os
import sys
import argparse
import logging
from typing import Optional

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("tavi-train")

def parse_args():
    parser = argparse.ArgumentParser(description="Fine-tune Gemma 3 1B IT for Tavi on-device assistant.")
    parser.add_argument("--model_id", type=str, default="google/gemma-3-1b-it", help="Base Hugging Face model repository or local path.")
    parser.add_argument("--train_file", type=str, default="training/data/tavi_train.jsonl", help="Path to training jsonl dataset.")
    parser.add_argument("--val_file", type=str, default="training/data/tavi_val.jsonl", help="Path to validation jsonl dataset.")
    parser.add_argument("--output_dir", type=str, default="training/output/gemma-3-1b-tavi-lora", help="Output directory for LoRA adapters.")
    parser.add_argument("--epochs", type=int, default=3, help="Number of training epochs.")
    parser.add_argument("--batch_size", type=int, default=4, help="Per-device train batch size.")
    parser.add_argument("--grad_accum", type=int, default=4, help="Gradient accumulation steps (effective batch = batch_size * grad_accum).")
    parser.add_argument("--lr", type=float, default=2e-4, help="Peak learning rate for AdamW.")
    parser.add_argument("--max_seq_length", type=int, default=1024, help="Maximum token sequence length (tuned for mobile context window).")
    parser.add_argument("--lora_r", type=int, default=16, help="LoRA rank dimension.")
    parser.add_argument("--lora_alpha", type=int, default=32, help="LoRA alpha scaling factor.")
    parser.add_argument("--lora_dropout", type=float, default=0.05, help="LoRA dropout rate.")
    parser.add_argument("--use_4bit", action="store_true", default=True, help="Use 4-bit bitsandbytes quantization (QLoRA) for low VRAM.")
    parser.add_argument("--no_4bit", dest="use_4bit", action="store_false", help="Disable 4-bit quantization and train in bfloat16 / float16.")
    parser.add_argument("--push_to_hub", action="store_true", help="Push trained LoRA adapter to Hugging Face Hub.")
    parser.add_argument("--hub_model_id", type=str, default=None, help="Destination HF Hub repo name (e.g. username/gemma-3-1b-tavi-lora).")
    return parser.parse_args()

def train():
    args = parse_args()
    logger.info("Initializing Tavi LoRA Fine-Tuning Pipeline...")
    logger.info(f"Base Model:       {args.model_id}")
    logger.info(f"Train Dataset:    {args.train_file}")
    logger.info(f"Output Adapter:   {args.output_dir}")
    logger.info(f"LoRA Rank/Alpha:  r={args.lora_r}, alpha={args.lora_alpha}")
    logger.info(f"Precision:        {'4-bit QLoRA (NF4)' if args.use_4bit else 'bfloat16 / float16'}")

    try:
        import torch
        from datasets import load_dataset
        from transformers import (
            AutoModelForCausalLM,
            AutoTokenizer,
            BitsAndBytesConfig,
            TrainingArguments,
        )
        from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
        from trl import SFTTrainer, SFTConfig
        # Ensure compatibility with Turing GPUs (Tesla T4 / compute capability < 8.0)
        if torch.cuda.is_available() and torch.cuda.get_device_capability()[0] < 8:
            try:
                import trl.kernels.chunked_logprob
                trl.kernels.chunked_logprob._MM_OUT_DTYPE = False
                logger.info("Configured TRL chunked_logprob for Turing (Compute < 8.0) FP16 GEMM fallback.")
            except Exception:
                pass
    except ImportError as e:
        logger.error(
            f"Required ML packages not installed: {e}\n"
            "Please install dependencies:\n"
            "pip install torch transformers datasets peft trl bitsandbytes accelerate"
        )
        sys.exit(1)

    # 1. Check datasets
    if not os.path.exists(args.train_file):
        logger.error(f"Train file not found: {args.train_file}. Run dataset_generator.py first!")
        sys.exit(1)

    dataset_dict = {"train": args.train_file}
    if os.path.exists(args.val_file):
        dataset_dict["validation"] = args.val_file

    dataset = load_dataset("json", data_files=dataset_dict)
    logger.info(f"Loaded dataset: {len(dataset['train'])} train samples, {len(dataset.get('validation', []))} val samples")

    # 2. Tokenizer
    logger.info("Loading tokenizer...")
    tokenizer = AutoTokenizer.from_pretrained(args.model_id, trust_remote_code=True)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token
    tokenizer.padding_side = "right"

    # 3. Model Configuration (Quantization & Device Setup)
    major = torch.cuda.get_device_capability()[0] if torch.cuda.is_available() else 0
    compute_dtype = torch.bfloat16 if major >= 8 else torch.float16
    logger.info(f"Target compute dtype: {compute_dtype} (CUDA major capability: {major})")

    bnb_config = None
    if args.use_4bit and torch.cuda.is_available():
        logger.info("Configuring 4-bit NormalFloat (NF4) quantization for QLoRA...")
        bnb_config = BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_quant_type="nf4",
            bnb_4bit_compute_dtype=compute_dtype,
            bnb_4bit_use_double_quant=True,
        )

    logger.info(f"Loading base model: {args.model_id}...")
    device_map = "auto" if torch.cuda.is_available() else None
    model = AutoModelForCausalLM.from_pretrained(
        args.model_id,
        quantization_config=bnb_config,
        device_map=device_map,
        dtype=compute_dtype,
        torch_dtype=compute_dtype,
        trust_remote_code=True,
    )

    if args.use_4bit and torch.cuda.is_available():
        model = prepare_model_for_kbit_training(model)

    # 4. LoRA Configuration
    # Targets all projection layers in Gemma attention + MLP blocks
    target_modules = [
        "q_proj",
        "k_proj",
        "v_proj",
        "o_proj",
        "gate_proj",
        "up_proj",
        "down_proj"
    ]

    peft_config = LoraConfig(
        r=args.lora_r,
        lora_alpha=args.lora_alpha,
        lora_dropout=args.lora_dropout,
        target_modules=target_modules,
        bias="none",
        task_type="CAUSAL_LM"
    )

    model = get_peft_model(model, peft_config)
    # Ensure all trainable LoRA parameters are float32 for AMP GradScaler compatibility on Turing/T4 GPUs
    for name, param in model.named_parameters():
        if param.requires_grad:
            param.data = param.data.to(torch.float32)

    trainable_params, all_param = model.get_nb_trainable_parameters()
    logger.info(
        f"LoRA Trainable Parameters: {trainable_params:,} / {all_param:,} "
        f"({100 * trainable_params / all_param:.3f}% of total weights)"
    )

    # 5. Training Arguments (SFTConfig for TRL)
    training_args = SFTConfig(
        output_dir=args.output_dir,
        dataset_text_field="text",
        max_seq_length=args.max_seq_length,
        num_train_epochs=args.epochs,
        per_device_train_batch_size=args.batch_size,
        gradient_accumulation_steps=args.grad_accum,
        learning_rate=args.lr,
        warmup_steps=30,
        lr_scheduler_type="cosine",
        logging_steps=25,
        eval_strategy="steps" if "validation" in dataset else "no",
        eval_steps=100 if "validation" in dataset else None,
        save_strategy="steps",
        save_steps=200,
        save_total_limit=2,
        fp16=False if args.use_4bit else (compute_dtype == torch.float16),
        bf16=False if args.use_4bit else (compute_dtype == torch.bfloat16),
        optim="paged_adamw_8bit" if (args.use_4bit and torch.cuda.is_available()) else "adamw_torch",
        report_to="none",
    )

    # 6. SFTTrainer
    logger.info("Initializing SFTTrainer...")
    trainer = SFTTrainer(
        model=model,
        train_dataset=dataset["train"],
        eval_dataset=dataset.get("validation"),
        args=training_args,
        processing_class=tokenizer,
    )

    logger.info("Starting training loop...")
    trainer.train()

    # 7. Save Adapter
    logger.info(f"Training complete! Saving LoRA adapter checkpoint to {args.output_dir}...")
    trainer.model.save_pretrained(args.output_dir)
    tokenizer.save_pretrained(args.output_dir)

    if args.push_to_hub and args.hub_model_id:
        logger.info(f"Pushing LoRA adapter to Hugging Face Hub: {args.hub_model_id}...")
        trainer.model.push_to_hub(args.hub_model_id)
        tokenizer.push_to_hub(args.hub_model_id)
        logger.info("Successfully pushed adapter to Hugging Face Hub!")

    logger.info(
        f"LoRA fine-tuning successful.\n"
        f"Next step: Merge adapters and convert to LiteRT FlatBuffer via:\n"
        f"  python training/export_litertlm.py --adapter_dir {args.output_dir}"
    )

if __name__ == "__main__":
    train()
