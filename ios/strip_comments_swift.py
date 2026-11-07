"""Quita los comentarios del cliente iOS (Swift y XML de recursos).

Uso:
    python strip_comments_swift.py [ruta]

`ruta` es la carpeta que se recorre; si se omite se usa la carpeta donde vive
este script. Se respetan las cadenas de texto (incluidas las que llevan `//`,
interpolaciones y cadenas raw) y los finales de línea originales. No se toca
`project.pbxproj` ni la carpeta `.xcodeproj`.
"""

import os
import sys
from pathlib import Path

# La consola de Windows puede no representar rutas con acentos combinados
# (por ejemplo el nombre NFD del .xcodeproj); no se debe abortar por eso.
try:
    sys.stdout.reconfigure(errors='replace')
    sys.stderr.reconfigure(errors='replace')
except Exception:
    pass


def compactar_lineas(text):
    """Quita espacios finales y deja como máximo una línea en blanco seguida."""
    nl = '\r\n' if '\r\n' in text else '\n'
    lineas = text.replace('\r\n', '\n').split('\n')
    salida = []
    vacias = 0
    for linea in lineas:
        linea = linea.rstrip(' \t')
        if linea == '':
            vacias += 1
            if vacias <= 1:
                salida.append('')
        else:
            vacias = 0
            salida.append(linea)
    return nl.join(salida)


def remove_swift_comments(text):
    out = []
    i = 0
    n = len(text)
    # Pila de contextos: código normal o código dentro de una interpolación
    # `\(...)`; las cadenas guardan su delimitador y el número de `#` si son raw.
    stack = [{'kind': 'code', 'interp': False, 'paren': 0, 'block': 0}]

    while i < n:
        ctx = stack[-1]
        ch = text[i]

        if ctx['kind'] == 'code':
            if ctx['block'] > 0:
                if text.startswith('/*', i):
                    ctx['block'] += 1
                    i += 2
                elif text.startswith('*/', i):
                    ctx['block'] -= 1
                    i += 2
                else:
                    if ch == '\n':
                        out.append(ch)
                    i += 1
                continue

            if text.startswith('//', i):
                i += 2
                while i < n and text[i] != '\n':
                    i += 1
                continue

            if text.startswith('/*', i):
                ctx['block'] = 1
                i += 2
                continue

            if ch == '#':
                j = i
                while j < n and text[j] == '#':
                    j += 1
                hashes = j - i
                if j < n and text[j] == '"':
                    if text.startswith('"""', j):
                        out.append(text[i:j + 3])
                        i = j + 3
                        stack.append({'kind': 'string', 'quote': '"""', 'raw': hashes})
                    else:
                        out.append(text[i:j + 1])
                        i = j + 1
                        stack.append({'kind': 'string', 'quote': '"', 'raw': hashes})
                    continue

            if text.startswith('"""', i):
                out.append('"""')
                i += 3
                stack.append({'kind': 'string', 'quote': '"""', 'raw': 0})
                continue

            if ch == '"':
                out.append('"')
                i += 1
                stack.append({'kind': 'string', 'quote': '"', 'raw': 0})
                continue

            if ctx['interp']:
                if ch == '(':
                    ctx['paren'] += 1
                    out.append(ch)
                    i += 1
                    continue
                if ch == ')':
                    if ctx['paren'] == 0:
                        out.append(ch)
                        i += 1
                        stack.pop()
                        continue
                    ctx['paren'] -= 1
                    out.append(ch)
                    i += 1
                    continue

            out.append(ch)
            i += 1
            continue

        # Contexto de cadena: se copia todo tal cual, salvo que empiece una
        # interpolación `\(` (o `\#(` en raw), que vuelve a código.
        raw = ctx['raw']
        quote = ctx['quote']

        if raw == 0:
            if ch == '\\':
                if i + 1 < n and text[i + 1] == '(':
                    out.append('\\(')
                    i += 2
                    stack.append({'kind': 'code', 'interp': True, 'paren': 0, 'block': 0})
                else:
                    out.append(ch)
                    if i + 1 < n:
                        out.append(text[i + 1])
                    i += 2
                continue

            if quote == '"""':
                if text.startswith('"""', i):
                    out.append('"""')
                    i += 3
                    stack.pop()
                else:
                    out.append(ch)
                    i += 1
                continue

            out.append(ch)
            i += 1
            if ch == '"':
                stack.pop()
            continue

        # Cadena raw: cierra con la comilla seguida del mismo número de `#`.
        if ch == '\\':
            j = i + 1
            k = 0
            while k < raw and j < n and text[j] == '#':
                j += 1
                k += 1
            if k == raw and j < n and text[j] == '(':
                out.append(text[i:j + 1])
                i = j + 1
                stack.append({'kind': 'code', 'interp': True, 'paren': 0, 'block': 0})
            else:
                out.append(ch)
                i += 1
            continue

        if quote == '"""':
            if text.startswith('"""', i):
                j = i + 3
                k = 0
                while k < raw and j < n and text[j] == '#':
                    j += 1
                    k += 1
                if k == raw:
                    out.append(text[i:j])
                    i = j
                    stack.pop()
                    continue
            out.append(ch)
            i += 1
            continue

        if ch == '"':
            j = i + 1
            k = 0
            while k < raw and j < n and text[j] == '#':
                j += 1
                k += 1
            if k == raw:
                out.append(text[i:j])
                i = j
                stack.pop()
                continue
        out.append(ch)
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
                if text[i] == '\n':
                    out.append('\n')
                i += 1
        elif state == 'STRING_DOUBLE':
            out.append(text[i])
            if text[i] == '\\' and i + 1 < n:
                out.append(text[i + 1])
                i += 2
            elif text[i] == '"':
                state = 'NORMAL'
                i += 1
            else:
                i += 1
        elif state == 'STRING_SINGLE':
            out.append(text[i])
            if text[i] == '\\' and i + 1 < n:
                out.append(text[i + 1])
                i += 2
            elif text[i] == "'":
                state = 'NORMAL'
                i += 1
            else:
                i += 1
    return ''.join(out)


def main():
    if len(sys.argv) > 1:
        base = Path(sys.argv[1])
    else:
        base = Path(__file__).resolve().parent

    if not base.exists():
        print(f'No existe la ruta: {base}')
        return 1

    modified = 0
    for root, dirs, files in os.walk(base):
        dirs[:] = [
            d for d in dirs
            if d not in ('.git', 'DerivedData', 'build', 'xcuserdata')
            and not d.endswith('.xcodeproj')
        ]
        for file in files:
            path = Path(root) / file
            if file.endswith('.swift'):
                remove = remove_swift_comments
            elif file.endswith(('.plist', '.entitlements', '.storyboard')):
                remove = remove_xml_comments
            else:
                continue
            with open(path, 'r', encoding='utf-8', newline='') as f:
                content = f.read()
            new_content = remove(content)
            if content != new_content:
                new_content = compactar_lineas(new_content)
                with open(path, 'w', encoding='utf-8', newline='') as f:
                    f.write(new_content)
                print(f'Comentarios quitados: {path}')
                modified += 1
    print(f'Total de archivos modificados: {modified}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
