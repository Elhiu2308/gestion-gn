import os

def remove_kotlin_comments(text):
    out = []
    i = 0
    n = len(text)
    state = 'NORMAL'

    while i < n:
        ch = text[i]
        if state == 'NORMAL':
            if text.startswith('"""', i):
                out.append('"""')
                i += 3
                state = 'RAW_STRING'
            elif text.startswith('//', i):
                i += 2
                state = 'LINE_COMMENT'
            elif text.startswith('/*', i):
                i += 2
                state = 'BLOCK_COMMENT'
            elif ch == '"':
                out.append(ch)
                i += 1
                state = 'STRING'
            elif ch == "'":
                out.append(ch)
                i += 1
                state = 'CHAR'
            else:
                out.append(ch)
                i += 1
        elif state == 'LINE_COMMENT':
            if ch == '\n':
                out.append(ch)
                i += 1
                state = 'NORMAL'
            else:
                i += 1
        elif state == 'BLOCK_COMMENT':
            if text.startswith('*/', i):
                i += 2
                state = 'NORMAL'
            else:
                i += 1
        elif state == 'STRING':
            out.append(ch)
            if ch == '\\' and i + 1 < n:
                out.append(text[i+1])
                i += 2
            elif ch == '"':
                state = 'NORMAL'
                i += 1
            else:
                i += 1
        elif state == 'RAW_STRING':
            if text.startswith('"""', i):
                out.append('"""')
                i += 3
                state = 'NORMAL'
            else:
                out.append(ch)
                i += 1
        elif state == 'CHAR':
            out.append(ch)
            if ch == '\\' and i + 1 < n:
                out.append(text[i+1])
                i += 2
            elif ch == "'":
                state = 'NORMAL'
                i += 1
            else:
                i += 1
    return ''.join(out)

def remove_xml_comments(text):
    out = []
    i = 0
    n = len(text)
    state = 'NORMAL'

    while i < n:
        if state == 'NORMAL':
            if text.startswith('<!--', i):
                i += 4
                state = 'COMMENT'
            elif text.startswith('"', i):
                out.append('"')
                i += 1
                state = 'STRING_DOUBLE'
            elif text.startswith("'", i):
                out.append("'")
                i += 1
                state = 'STRING_SINGLE'
            else:
                out.append(text[i])
                i += 1
        elif state == 'COMMENT':
            if text.startswith('-->', i):
                i += 3
                state = 'NORMAL'
            else:
                i += 1
        elif state == 'STRING_DOUBLE':
            out.append(text[i])
            if text[i] == '\\' and i + 1 < n:
                out.append(text[i+1])
                i += 2
            elif text[i] == '"':
                state = 'NORMAL'
                i += 1
            else:
                out.append(text[i])
                i += 1
        elif state == 'STRING_SINGLE':
            out.append(text[i])
            if text[i] == '\\' and i + 1 < n:
                out.append(text[i+1])
                i += 2
            elif text[i] == "'":
                state = 'NORMAL'
                i += 1
            else:
                out.append(text[i])
                i += 1
    return ''.join(out)

def main():
    main_dir = '/Users/estebangarciajuarez/Desktop/Android/app/src/main'
    modified_count = 0
    for root, dirs, files in os.walk(main_dir):
        for file in files:
            path = os.path.join(root, file)
            if file.endswith('.kt'):
                with open(path, 'r', encoding='utf-8') as f:
                    content = f.read()
                new_content = remove_kotlin_comments(content)
                if content != new_content:
                    with open(path, 'w', encoding='utf-8') as f:
                        f.write(new_content)
                    print(f"Cleaned Kotlin comments in: {path}")
                    modified_count += 1
            elif file.endswith('.xml'):
                with open(path, 'r', encoding='utf-8') as f:
                    content = f.read()
                new_content = remove_xml_comments(content)
                if content != new_content:
                    with open(path, 'w', encoding='utf-8') as f:
                        f.write(new_content)
                    print(f"Cleaned XML comments in: {path}")
                    modified_count += 1
    print(f"Total files modified: {modified_count}")

if __name__ == '__main__':
    main()
