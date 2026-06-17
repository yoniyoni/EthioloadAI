import json
import os

jsonl_path = r"C:/Users/Yonas/.claude/projects/c--Users-Yonas-Herd-EthioLoadAI/ca8e7a21-bacb-43b0-aa9f-166cc1ce9c23.jsonl"

missing_files = [
    "app_router.dart",
    "models.dart",
    "data_providers.dart",
    "repositories.dart",
    "main.dart",
    "placeholder_screens.dart",
]

# We'll look for Write OR Edit tool calls for missing files
writes = {}
edits = {}

line_num = 0
with open(jsonl_path, 'r', encoding='utf-8') as f:
    for line in f:
        line_num += 1
        line = line.strip()
        if not line:
            continue
        try:
            obj = json.loads(line)
        except:
            continue

        msg = obj.get('message', obj)
        content = msg.get('content', [])
        if isinstance(content, list):
            for item in content:
                if not isinstance(item, dict):
                    continue
                tool_name = item.get('name', '')
                if item.get('type') != 'tool_use':
                    continue
                if tool_name not in ('Write', 'Edit'):
                    continue
                inp = item.get('input', {})
                fp = inp.get('file_path', '')
                fname = fp.replace('\\\\', '/').replace('\\', '/').split('/')[-1]
                if fname in missing_files:
                    if tool_name == 'Write':
                        content_val = inp.get('content', '')
                        if fname not in writes:
                            writes[fname] = []
                        writes[fname].append({
                            'line': line_num,
                            'path': fp,
                            'content_len': len(content_val),
                            'content': content_val,
                        })
                    elif tool_name == 'Edit':
                        if fname not in edits:
                            edits[fname] = []
                        edits[fname].append({
                            'line': line_num,
                            'path': fp,
                            'old_string': inp.get('old_string', ''),
                            'new_string': inp.get('new_string', ''),
                        })

print(f"Total lines processed: {line_num}")
print(f"\nWrite calls found:")
for fname, calls in writes.items():
    print(f"  {fname}: {len(calls)} write(s)")
    for c in calls:
        print(f"    line {c['line']}: content_len={c['content_len']}")

print(f"\nEdit calls found:")
for fname, calls in edits.items():
    print(f"  {fname}: {len(calls)} edit(s)")
    for c in calls:
        print(f"    line {c['line']}")

print("\nStill missing:")
for f in missing_files:
    if f not in writes and f not in edits:
        print(f"  {f}")
