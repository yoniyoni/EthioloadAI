import json
import os

jsonl_path = r"C:/Users/Yonas/.claude/projects/c--Users-Yonas-Herd-EthioLoadAI/ca8e7a21-bacb-43b0-aa9f-166cc1ce9c23.jsonl"

# First find the tool_use_id for the placeholder_screens Read at line 6783
# Then find the tool_result with that ID

placeholder_read_tool_use_ids = []

line_num = 0
all_tool_use_ids_for_placeholder = {}

with open(jsonl_path, 'r', encoding='utf-8') as f:
    for line in f:
        line_num += 1
        line = line.strip()
        if not line:
            continue
        if 'placeholder' not in line.lower():
            continue
        try:
            obj = json.loads(line)
        except:
            continue
        msg = obj.get('message', obj)
        content = msg.get('content', [])
        if isinstance(content, list):
            for item in content:
                if isinstance(item, dict) and item.get('type') == 'tool_use' and item.get('name') == 'Read':
                    inp = item.get('input', {})
                    fp = inp.get('file_path', '')
                    if 'placeholder' in fp.lower():
                        tool_id = item.get('id', '')
                        print(f"Line {line_num}: Read tool_use_id={tool_id}, path={fp}")
                        all_tool_use_ids_for_placeholder[tool_id] = line_num

print(f"\nNow searching for tool_results for these IDs...")
# Now find the corresponding tool_result
target_ids = set(all_tool_use_ids_for_placeholder.keys())
line_num = 0
with open(jsonl_path, 'r', encoding='utf-8') as f:
    for line in f:
        line_num += 1
        line = line.strip()
        if not line:
            continue
        # Quick check
        found_any = any(tid in line for tid in target_ids)
        if not found_any:
            continue
        try:
            obj = json.loads(line)
        except:
            continue
        msg = obj.get('message', obj)
        content = msg.get('content', [])
        if isinstance(content, list):
            for item in content:
                if isinstance(item, dict) and item.get('type') == 'tool_result':
                    tid = item.get('tool_use_id', '')
                    if tid in target_ids:
                        inner = item.get('content', [])
                        if isinstance(inner, list):
                            for i2 in inner:
                                if isinstance(i2, dict) and i2.get('type') == 'text':
                                    text = i2.get('text', '')
                                    print(f"\nLine {line_num}: Found tool_result for {tid} (from Read at line {all_tool_use_ids_for_placeholder[tid]})")
                                    print(f"Content length: {len(text)}")
                                    print(f"First 300 chars: {text[:300]}")
