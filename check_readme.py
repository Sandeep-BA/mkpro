import re

with open('README.md', 'r', encoding='utf-8') as f:
    content = f.read()

# Look for version patterns
versions = re.findall(r'\bv?\d+\.\d+(?:\.\d+)?(?:-SNAPSHOT)?\b', content)
print("Found versions:", versions)

# Look for the SysAdmin line to confirm it's there
sysadmin_line = re.findall(r'\| \*\*SysAdmin\*\* \| .* \|', content)
print("Found SysAdmin line:", sysadmin_line)
