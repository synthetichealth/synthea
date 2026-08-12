#!/bin/bash
# find_broken_json.sh - Find the actual file causing the JSONPath error

echo "🔍 Searching for broken JSON files..."
echo "=================================="

# 1. Search for the exact truncated pattern
echo "📋 Searching for truncated 'conditio' pattern..."
find . -name "*.json" -type f -exec grep -l '"conditio"' {} \; 2>/dev/null | while read file; do
    echo "❌ FOUND TRUNCATED PATTERN in: $file"
    echo "Context:"
    grep -n -B 3 -A 3 '"conditio"' "$file"
    echo "---"
done

# 2. Search for any incomplete condition structures
echo "📋 Searching for incomplete condition patterns..."
find . -name "*.json" -type f -exec grep -l '"condition":\s*{[^}]*$' {} \; 2>/dev/null | while read file; do
    echo "⚠️  Potential incomplete condition in: $file"
    grep -n -B 2 -A 2 '"condition":\s*{[^}]*$' "$file"
    echo "---"
done

# 3. Validate all JSON files in modules directory
echo "📋 Validating all JSON files..."
find src/main/resources/modules -name "*.json" -type f | while read file; do
    if python3 -m json.tool "$file" >/dev/null 2>&1; then
        echo "✅ $file"
    else
        echo "❌ INVALID JSON: $file"
        echo "Error details:"
        python3 -m json.tool "$file" 2>&1 | head -3
        echo "---"
    fi
done

# 4. Check build directory for cached files
echo "📋 Checking build directory..."
if [ -d "build" ]; then
    find build -name "*.json" -type f -exec grep -l '"conditio"' {} \; 2>/dev/null | while read file; do
        echo "❌ FOUND TRUNCATED PATTERN in BUILD FILE: $file"
    done
fi

# 5. Check for any backup or temporary files
echo "📋 Checking for backup/temp files..."
find . -name "*.json.*" -o -name "*.json~" -o -name "*.bak" | while read file; do
    echo "📄 Found backup/temp file: $file"
    if grep -q '"conditio"' "$file" 2>/dev/null; then
        echo "❌ CONTAINS TRUNCATED PATTERN!"
    fi
done

echo "✅ Search complete!"
