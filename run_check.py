import subprocess
import sys

result = subprocess.run([sys.executable, 'check_readme.py'], capture_output=True, text=True)
print(result.stdout)
print(result.stderr)
