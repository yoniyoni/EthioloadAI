import json
import os
import re

jsonl_path = r"C:/Users/Yonas/.claude/projects/c--Users-Yonas-Herd-EthioLoadAI/ca8e7a21-bacb-43b0-aa9f-166cc1ce9c23.jsonl"

target_files_map = {
    "app_router.dart": r"C:/Users/Yonas/Herd/EthioLoadAI/frontend/artifacts/mobile-app/lib/src/config/routes/app_router.dart",
    "models.dart": r"C:/Users/Yonas/Herd/EthioLoadAI/frontend/artifacts/mobile-app/lib/src/data/models/models.dart",
    "data_providers.dart": r"C:/Users/Yonas/Herd/EthioLoadAI/frontend/artifacts/mobile-app/lib/src/data/providers/data_providers.dart",
    "repositories.dart": r"C:/Users/Yonas/Herd/EthioLoadAI/frontend/artifacts/mobile-app/lib/src/data/repositories/repositories.dart",
    "auth_screens.dart": r"C:/Users/Yonas/Herd/EthioLoadAI/frontend/artifacts/mobile-app/lib/src/features/auth/auth_screens.dart",
    "my_bookings_screen.dart": r"C:/Users/Yonas/Herd/EthioLoadAI/frontend/artifacts/mobile-app/lib/src/features/bookings/my_bookings_screen.dart",
    "driver_dashboard_screen.dart": r"C:/Users/Yonas/Herd/EthioLoadAI/frontend/artifacts/mobile-app/lib/src/features/driver/driver_dashboard_screen.dart",
    "freight_detail_screen.dart": r"C:/Users/Yonas/Herd/EthioLoadAI/frontend/artifacts/mobile-app/lib/src/features/shipper/freight_detail_screen.dart",
    "create_freight_screen.dart": r"C:/Users/Yonas/Herd/EthioLoadAI/frontend/artifacts/mobile-app/lib/src/features/shipper/create_freight_screen.dart",
    "main.dart": r"C:/Users/Yonas/Herd/EthioLoadAI/frontend/artifacts/mobile-app/lib/main.dart",
    "placeholder_screens.dart": r"C:/Users/Yonas/Herd/EthioLoadAI/frontend/artifacts/mobile-app/lib/src/features/placeholder_screens.dart",
}

# Collect Write calls (full content) and Edit calls
write_calls = {}  # fname -> list of {line, path, content}
edit_calls = {}   # fname -> list of {line, path, old_string, new_string, replace_all}
read_results = {} # fname -> list of {line, tool_use_id, content} (from tool_result)

# Also capture Read tool_use_ids for these files
read_tool_use_ids = {}  # tool_use_id -> fname

line_num = 0
with open(jsonl_path, 'r', encoding='utf-8') as f:
    for line in f:
        line_num += 1
        line = line.strip()
        if not line:
            continue

        # Quick filter - check if any target filename appears
        found = False
        for fname in target_files_map:
            if fname in line:
                found = True
                break
        if not found:
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

                item_type = item.get('type', '')

                # Tool use (assistant writes/edits files)
                if item_type == 'tool_use':
                    tool_name = item.get('name', '')
                    inp = item.get('input', {})
                    fp = inp.get('file_path', '')
                    fname = fp.replace('\\\\', '/').replace('\\', '/').split('/')[-1]

                    if fname not in target_files_map:
                        continue

                    if tool_name == 'Write':
                        content_val = inp.get('content', '')
                        if fname not in write_calls:
                            write_calls[fname] = []
                        write_calls[fname].append({
                            'line': line_num,
                            'path': fp,
                            'content': content_val,
                        })

                    elif tool_name == 'Edit':
                        if fname not in edit_calls:
                            edit_calls[fname] = []
                        edit_calls[fname].append({
                            'line': line_num,
                            'path': fp,
                            'old_string': inp.get('old_string', ''),
                            'new_string': inp.get('new_string', ''),
                            'replace_all': inp.get('replace_all', False),
                        })

                    elif tool_name == 'Read':
                        tool_use_id = item.get('id', '')
                        if tool_use_id:
                            read_tool_use_ids[tool_use_id] = (fname, line_num)

                # Tool result (user provides file content after Read)
                elif item_type == 'tool_result':
                    tid = item.get('tool_use_id', '')
                    if tid in read_tool_use_ids:
                        fname, read_line = read_tool_use_ids[tid]
                        result_content = item.get('content', '')
                        if isinstance(result_content, str):
                            text = result_content
                        elif isinstance(result_content, list):
                            text = ''
                            for rc in result_content:
                                if isinstance(rc, dict) and rc.get('type') == 'text':
                                    text = rc.get('text', '')
                                    break
                        else:
                            text = ''

                        if fname not in read_results:
                            read_results[fname] = []
                        read_results[fname].append({
                            'line': line_num,
                            'read_line': read_line,
                            'content': text,
                        })

print("=== WRITE CALLS ===")
for fname, calls in write_calls.items():
    print(f"{fname}: {len(calls)} write(s)")
    for c in calls:
        print(f"  line {c['line']}: content_len={len(c['content'])}")

print("\n=== EDIT CALLS ===")
for fname, calls in edit_calls.items():
    print(f"{fname}: {len(calls)} edit(s)")
    for c in calls:
        print(f"  line {c['line']}: old_len={len(c['old_string'])}, new_len={len(c['new_string'])}, replace_all={c['replace_all']}")

print("\n=== READ RESULTS (file content from tool results) ===")
for fname, results in read_results.items():
    print(f"{fname}: {len(results)} read result(s)")
    for r in results:
        print(f"  read at line {r['read_line']}, result at line {r['line']}: content_len={len(r['content'])}")
