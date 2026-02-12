import re
import os

file_path = "src/main/java/com/mkpro/MkPro.java"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

changes = {
    "action_logger": 0,
    "summary_path": 0,
    "team_selection": 0,
    "session_id": 0,
    "save_methods": 0,
    "main_cleanup": 0
}

pattern1 = r"ActionLogger\\s+logger\\s+=\\s+new\\s+ActionLogger\\(\"mkpro_logs\\.db\"\\);"
replacement1 = "ActionLogger logger = new ActionLogger(Paths.get(LOCAL_CONFIG_DIR, \"mkpro_logs.db\").toString());"
content, count = re.subn(pattern1, replacement1, content)
changes["action_logger"] = count

pattern2 = r"Path\\s+summaryPath\\s+=\\s+Paths\\.get\\(\"session_summary\\.txt\"\\);"
replacement2 = "Path summaryPath = Paths.get(LOCAL_CONFIG_DIR, \"session_summary.txt\");"
content, count = re.subn(pattern2, replacement2, content)
changes["summary_path"] = count

pattern3 = r"Paths\\.get\\(System\\.getProperty\\(\"user\\.home\"\\),\\s*\"\\.mkpro\",\\s*\"team_selection\"\\)"
replacement3 = "Paths.get(LOCAL_CONFIG_DIR, \"team_selection\")"
content, count = re.subn(pattern3, replacement3, content)
changes["team_selection"] = count

pattern4 = r"Paths\\.get\\(System\\.getProperty\\(\"user\\.home\"\\),\\s*\"\\.mkpro\",\\s*\"session_id\"\\)"
replacement4 = "Paths.get(LOCAL_CONFIG_DIR, \"session_id\")"
content, count = re.subn(pattern4, replacement4, content)
changes["session_id"] = count

pattern5 = r"Path\\s+mkproDir\\s+=\\s+Paths\\.get\\(System\\.getProperty\\(\"user\\.home\"\\),\\s*\"\\.mkpro\"\\);"
replacement5 = "Path mkproDir = Paths.get(LOCAL_CONFIG_DIR);"
content, count = re.subn(pattern5, replacement5, content)
changes["save_methods"] = count

pattern6 = r"try\\s*{\\s*java\\.nio\\.file\\.Files\\.createDirectories\\(java\\.nio\\.file\\.Paths\\.get\\(\"\\.mkpro\"\\)\\);\\s*}\\s*catch\\s*\\(Exception\\s+e\\)\\s*{\\s*}"
content, count = re.subn(pattern6, "", content)
changes["main_cleanup"] = count

with open(file_path, "w", encoding="utf-8", newline="") as f:
    f.write(content)

for k, v in changes.items():
    print(f"{k}: {v}")
