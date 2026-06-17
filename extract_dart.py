import json
import os

jsonl_path = r"C:/Users/Yonas/.claude/projects/c--Users-Yonas-Herd-EthioLoadAI/ca8e7a21-bacb-43b0-aa9f-166cc1ce9c23.jsonl"

target_files = [
    "app_router.dart",
    "models.dart",
    "data_providers.dart",
    "repositories.dart",
    "auth_screens.dart",
    "my_bookings_screen.dart",
    "driver_dashboard_screen.dart",
    "freight_detail_screen.dart",
    "create_freight_screen.dart",
    "main.dart",
    "placeholder_screens.dart",
]

# We'll collect all Write calls for each target
writes = {}

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

        # Look for assistant messages with tool_use of type Write
        msg = obj.get('message', obj)
        content = msg.get('content', [])
        if isinstance(content, list):
            for item in content:
                if isinstance(item, dict) and item.get('type') == 'tool_use' and item.get('name') == 'Write':
                    inp = item.get('input', {})
                    fp = inp.get('file_path', '')
                    # Normalize path separators
                    fname = fp.replace('\\\\', '/').replace('\\', '/').split('/')[-1]
                    if fname in target_files:
                        content_val = inp.get('content', '')
                        if fname not in writes:
                            writes[fname] = []
                        writes[fname].append({
                            'line': line_num,
                            'path': fp,
                            'content_len': len(content_val),
                            'content': content_val,
                        })

print(f"Total lines processed: {line_num}")
print(f"\nFound Write calls for:")
for fname, calls in writes.items():
    print(f"  {fname}: {len(calls)} write(s)")
    for c in calls:
        print(f"    line {c['line']}: path={c['path']}, content_len={c['content_len']}")

print("\nMissing files:")
for f in target_files:
    if f not in writes:
        print(f"  MISSING: {f}")
