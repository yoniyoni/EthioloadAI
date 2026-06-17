import json
import os

jsonl_path = r"C:/Users/Yonas/.claude/projects/c--Users-Yonas-Herd-EthioLoadAI/ca8e7a21-bacb-43b0-aa9f-166cc1ce9c23.jsonl"

# Search for placeholder_screens.dart - maybe it was written in pieces or different name
line_num = 0
matches = []
with open(jsonl_path, 'r', encoding='utf-8') as f:
    for line in f:
        line_num += 1
        line = line.strip()
        if not line:
            continue
        if 'placeholder' in line.lower():
            try:
                obj = json.loads(line)
            except:
                continue
            msg = obj.get('message', obj)
            content = msg.get('content', [])
            if isinstance(content, list):
                for item in content:
                    if isinstance(item, dict) and item.get('type') == 'tool_use':
                        tool_name = item.get('name', '')
                        inp = item.get('input', {})
                        fp = inp.get('file_path', '')
                        if 'placeholder' in fp.lower():
                            print(f"Line {line_num}: tool={tool_name}, path={fp}, keys={list(inp.keys())}")
                            if tool_name == 'Write':
                                print(f"  content_len={len(inp.get('content',''))}")
                            elif tool_name == 'Edit':
                                print(f"  old_len={len(inp.get('old_string',''))}, new_len={len(inp.get('new_string',''))}")

print(f"Total lines: {line_num}")
