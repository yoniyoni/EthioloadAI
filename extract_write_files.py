import json
import os

jsonl_path = r"C:/Users/Yonas/.claude/projects/c--Users-Yonas-Herd-EthioLoadAI/ca8e7a21-bacb-43b0-aa9f-166cc1ce9c23.jsonl"

# Files that have Write calls - we want the LAST (highest line number) version
write_targets = {
    "driver_dashboard_screen.dart": {
        "dest": r"C:/Users/Yonas/Herd/EthioLoadAI/frontend/artifacts/mobile-app/lib/src/features/driver/driver_dashboard_screen.dart",
        "last_line": 4716,
    },
    "freight_detail_screen.dart": {
        "dest": r"C:/Users/Yonas/Herd/EthioLoadAI/frontend/artifacts/mobile-app/lib/src/features/shipper/freight_detail_screen.dart",
        "last_line": 6810,
    },
    "auth_screens.dart": {
        "dest": r"C:/Users/Yonas/Herd/EthioLoadAI/frontend/artifacts/mobile-app/lib/src/features/auth/auth_screens.dart",
        "last_line": 6795,
    },
    "my_bookings_screen.dart": {
        "dest": r"C:/Users/Yonas/Herd/EthioLoadAI/frontend/artifacts/mobile-app/lib/src/features/bookings/my_bookings_screen.dart",
        "last_line": 6708,
    },
    "create_freight_screen.dart": {
        "dest": r"C:/Users/Yonas/Herd/EthioLoadAI/frontend/artifacts/mobile-app/lib/src/features/shipper/create_freight_screen.dart",
        "last_line": 7169,
    },
}

# Collect the last Write content for each target
collected = {}
target_lines = {v['last_line'] for v in write_targets.values()}

line_num = 0
with open(jsonl_path, 'r', encoding='utf-8') as f:
    for line in f:
        line_num += 1
        if line_num not in target_lines:
            continue
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
                if isinstance(item, dict) and item.get('type') == 'tool_use' and item.get('name') == 'Write':
                    inp = item.get('input', {})
                    fp = inp.get('file_path', '')
                    fname = fp.replace('\\\\', '/').replace('\\', '/').split('/')[-1]
                    if fname in write_targets and write_targets[fname]['last_line'] == line_num:
                        file_content = inp.get('content', '')
                        collected[fname] = file_content
                        print(f"Found {fname} at line {line_num}: {len(file_content)} chars")

print(f"\nCollected {len(collected)} files")
print("Writing files...")

for fname, file_content in collected.items():
    dest = write_targets[fname]['dest']
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with open(dest, 'w', encoding='utf-8') as f:
        f.write(file_content)
    print(f"  Written: {dest} ({len(file_content)} chars)")

# Check which ones we missed
for fname in write_targets:
    if fname not in collected:
        print(f"  MISSING: {fname}")
