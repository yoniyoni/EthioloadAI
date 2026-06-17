import json
import os

jsonl_path = r"C:/Users/Yonas/.claude/projects/c--Users-Yonas-Herd-EthioLoadAI/ca8e7a21-bacb-43b0-aa9f-166cc1ce9c23.jsonl"

# placeholder_screens was only Read, never Written. Let's check what Edit ops look like for it by checking all tool_use ops on it
# Also look for it in any Write ops with different path or any content context

# Also: let's capture all tool results (Read results) for placeholder_screens at line 6783 to see what content was there then
line_num = 0
results_to_check = {452, 1640, 2926, 4208, 4549, 6783}
# Check the line AFTER each Read to see the tool_result

lines_buffer = {}
with open(jsonl_path, 'r', encoding='utf-8') as f:
    for line in f:
        line_num += 1
        # We need to look at tool results. Let's check lines around those line numbers
        if line_num in range(6780, 6800):
            print(f"Line {line_num}: {line[:200]}")
