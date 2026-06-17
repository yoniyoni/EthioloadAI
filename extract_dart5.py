import json
import os

jsonl_path = r"C:/Users/Yonas/.claude/projects/c--Users-Yonas-Herd-EthioLoadAI/ca8e7a21-bacb-43b0-aa9f-166cc1ce9c23.jsonl"

# Find the tool_result for placeholder_screens.dart Read at line 6783+
# The tool result will be in a user message after the assistant's Read call
# Let's look at lines around 6783-6790

target_lines = list(range(6783, 6795))
line_num = 0
with open(jsonl_path, 'r', encoding='utf-8') as f:
    for line in f:
        line_num += 1
        if line_num in target_lines:
            line = line.strip()
            try:
                obj = json.loads(line)
                msg = obj.get('message', obj)
                role = msg.get('role', obj.get('type', ''))
                content = msg.get('content', [])
                if isinstance(content, list):
                    for item in content:
                        if isinstance(item, dict):
                            t = item.get('type', '')
                            if t == 'tool_result':
                                tool_use_id = item.get('tool_use_id', '')
                                inner = item.get('content', [])
                                if isinstance(inner, list):
                                    for i2 in inner:
                                        if isinstance(i2, dict) and i2.get('type') == 'text':
                                            print(f"Line {line_num} tool_result for {tool_use_id}: len={len(i2.get('text',''))}")
                                            print(i2.get('text','')[:500])
                                            print("---")
                            elif t == 'tool_use':
                                print(f"Line {line_num} tool_use: name={item.get('name')}, file_path={item.get('input',{}).get('file_path','')}")
            except Exception as e:
                print(f"Line {line_num}: Error: {e}")
