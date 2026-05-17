import sys

with open(r"c:\Users\kth00\StudioProjects\InsightDeal\app\src\main\java\com\ddaeany0919\insightdeal\presentation\home\HomeScreen.kt", "r", encoding="utf-8") as f:
    content = f.read()

opens = content.count("{")
closes = content.count("}")

print(f"Total '{{': {opens}")
print(f"Total '}}': {closes}")
if opens > closes:
    print(f"Missing {opens - closes} closing braces '}}'")
elif closes > opens:
    print(f"Missing {closes - opens} opening braces '{{'")
else:
    print("Braces are balanced globally (but they might still be misaligned).")

# Let's find the unclosed braces line by line
stack = []
for i, line in enumerate(content.split("\n")):
    for j, char in enumerate(line):
        if char == "{":
            stack.append((i+1, j+1, line.strip()))
        elif char == "}":
            if stack:
                stack.pop()
            else:
                print(f"Unmatched closing brace at line {i+1}")

if stack:
    print("Unclosed braces:")
    for item in stack:
        print(f"Line {item[0]}: {item[2]}")
