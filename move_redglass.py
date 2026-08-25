import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# find "fun Modifier.redGlass(" and everything after it
redglass_idx = content.find("fun Modifier.redGlass(")
if redglass_idx != -1:
    redglass_code = content[redglass_idx:]
    new_content = content[:redglass_idx]
    
    with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
        f.write(new_content)
        
    with open("app/src/main/java/com/example/ui/components/AllComponents.kt", "a") as f:
        f.write("\n" + redglass_code)

