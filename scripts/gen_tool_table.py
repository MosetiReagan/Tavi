#!/usr/bin/env python3
"""
Tavi — Native Tool Table Generator
Extracts native Android tools declared in the Tavi codebase and emits a clean Markdown table.
"""

import os
import re
import sys

TOOLS_DIR = "app/src/main/java/com/example/tavi/tools"
README_PATH = "README.md"

def find_tools():
    tools = []
    for root, _, files in os.walk(TOOLS_DIR):
        for file in files:
            if file.endswith(".kt"):
                path = os.path.join(root, file)
                with open(path, "r", encoding="utf-8") as f:
                    content = f.read()

                # Find classes implementing AssistantTool
                class_matches = re.finditer(r"class\s+(\w+)\s*:\s*AssistantTool\b", content)
                for cm in class_matches:
                    start_pos = cm.start()
                    # Find matching braces or next class to bound the search
                    sub = content[start_pos:start_pos + 2500]

                    name_match = re.search(r'override\s+val\s+name\s*:\s*String\s*=\s*"([^"]+)"', sub)
                    cat_match = re.search(r'override\s+val\s+category\s*:\s*ToolCategory\s*=\s*ToolCategory\.(\w+)', sub)
                    desc_match = re.search(r'override\s+val\s+description\s*:\s*String\s*=\s*"([^"]+)"', sub)
                    conf_match = re.search(r'override\s+val\s+requiresConfirmation\s*:\s*Boolean\s*=\s*(true|false)', sub)

                    if name_match:
                        t_name = name_match.group(1)
                        t_cat = cat_match.group(1) if cat_match else "SYSTEM"
                        t_desc = desc_match.group(1) if desc_match else ""
                        t_conf = conf_match.group(1).lower() == "true" if conf_match else False

                        tools.append({
                            "name": t_name,
                            "category": t_cat,
                            "description": t_desc,
                            "confirmation": "🔒 Required" if t_conf else "⚡ Direct",
                            "file": file
                        })
    # Sort alphabetically by category, then name
    tools.sort(key=lambda t: (t["category"], t["name"]))
    return tools

def generate_markdown_table(tools):
    lines = [
        f"### Complete Native Tool Directory ({len(tools)} On-Device Tools)\n",
        "| Category | Tool Identifier | Consequential Gate | Purpose |",
        "| :--- | :--- | :---: | :--- |"
    ]
    for t in tools:
        lines.append(f"| **{t['category']}** | `{t['name']}` | {t['confirmation']} | {t['description']} |")
    return "\n".join(lines)

def main():
    tools = find_tools()
    print(f"Discovered {len(tools)} native AssistantTool implementations:")
    for t in tools:
        print(f"  - [{t['category']}] {t['name']} ({t['confirmation']}): {t['description'][:50]}...")

    table_md = generate_markdown_table(tools)

    if "--update-readme" in sys.argv and os.path.exists(README_PATH):
        with open(README_PATH, "r", encoding="utf-8") as f:
            readme = f.read()

        start_marker = "<!-- TOOL_TABLE_START -->"
        end_marker = "<!-- TOOL_TABLE_END -->"

        if start_marker in readme and end_marker in readme:
            new_readme = re.sub(
                f"{re.escape(start_marker)}.*?{re.escape(end_marker)}",
                f"{start_marker}\n\n{table_md}\n\n{end_marker}",
                readme,
                flags=re.DOTALL
            )
            with open(README_PATH, "w", encoding="utf-8") as f:
                f.write(new_readme)
            print(f"\nUpdated {README_PATH} between {start_marker} and {end_marker}")
        else:
            print("\nNote: markers <!-- TOOL_TABLE_START --> and <!-- TOOL_TABLE_END --> not found in README. Emitting table to stdout:")
            print("\n" + table_md)
    else:
        print("\n" + table_md)

if __name__ == "__main__":
    main()
