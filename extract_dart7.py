import json
import os

jsonl_path = r"C:/Users/Yonas/.claude/projects/c--Users-Yonas-Herd-EthioLoadAI/ca8e7a21-bacb-43b0-aa9f-166cc1ce9c23.jsonl"

# Check how user messages with tool results are structured
# Find the user message right after line 452 (placeholder read)
target_line = 453

line_num = 0
with open(jsonl_path, 'r', encoding='utf-8') as f:
    for line in f:
        line_num += 1
        if line_num in range(453, 470):
            line = line.strip()
            if line:
                print(f"Line {line_num}: {line[:400]}")
                print()
