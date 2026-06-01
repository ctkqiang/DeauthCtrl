import re
import os
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))

def clean_trailing_whitespace_and_blanks(text):
    lines = text.split('\n')
    cleaned = []
    for line in lines:
        stripped = line.rstrip()
        if stripped == '' and (cleaned and cleaned[-1] == ''):
            continue
        cleaned.append(stripped)
    while cleaned and cleaned[-1] == '':
        cleaned.pop()
    if cleaned and cleaned[0] == '':
        cleaned.pop(0)
    return '\n'.join(cleaned)

def remove_block_comments(text):
    return re.sub(r'/\*[\s\S]*?\*/', '', text)

def remove_single_line_comments(text):
    lines = text.split('\n')
    result = []
    for line in lines:
        stripped = line.lstrip()
        if stripped.startswith('//') or stripped.startswith('#'):
            continue
        pos = line.find('//')
        if pos != -1:
            if pos > 0 and line[pos - 1] == ':':
                result.append(line)
            else:
                before = line[:pos].rstrip()
                if before.strip():
                    result.append(before)
        elif '#' in line and not stripped.startswith('package') and not stripped.startswith('import'):
            pass
        else:
            result.append(line)
    return '\n'.join(result)

def remove_xml_comments(text):
    return re.sub(r'<!--[\s\S]*?-->', '', text)

def process_kotlin(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    original = content
    content = remove_block_comments(content)
    content = remove_single_line_comments(content)
    content = clean_trailing_whitespace_and_blanks(content)
    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False

def process_xml(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    original = content
    content = remove_xml_comments(content)
    content = clean_trailing_whitespace_and_blanks(content)
    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False

def process_properties(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    original = content
    lines = content.split('\n')
    result = []
    for line in lines:
        stripped = line.lstrip()
        if stripped.startswith('#') or stripped.startswith('//'):
            continue
        result.append(line)
    content = '\n'.join(result)
    content = clean_trailing_whitespace_and_blanks(content)
    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False

def main():
    targets = set()
    for dirpath, dirnames, filenames in os.walk(ROOT):
        if '.git' in dirpath or 'gradle/wrapper' in dirpath or 'build' in dirpath:
            continue
        for fn in filenames:
            fp = os.path.join(dirpath, fn)
            targets.add(fp)

    kotlin_exts = {'.kt', '.kts'}
    xml_exts = {'.xml'}
    prop_exts = {'.properties', '.pro'}

    processed = 0
    for fp in sorted(targets):
        ext = os.path.splitext(fp)[1].lower()
        if ext in kotlin_exts:
            if process_kotlin(fp):
                processed += 1
                print(f'  [KOTLIN] {os.path.relpath(fp, ROOT)}')
        elif ext in xml_exts:
            if process_xml(fp):
                processed += 1
                print(f'  [XML]    {os.path.relpath(fp, ROOT)}')
        elif ext in prop_exts or fp.endswith('.pro'):
            if process_properties(fp):
                processed += 1
                print(f'  [PROPS]  {os.path.relpath(fp, ROOT)}')

    print(f'\n  处理完成: {processed} 个文件被修改')

if __name__ == '__main__':
    main()