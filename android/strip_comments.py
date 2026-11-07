"""Quita los comentarios del cliente Android (Kotlin y XML).

Uso:
    python strip_comments.py [ruta]

`ruta` es la carpeta que se recorre; si se omite se usa
`app/src/main` del proyecto donde vive este script. Sólo se tocan archivos
`.kt`, `.kts` y `.xml`; se conservan las cadenas de texto (incluidas las
URLs con `//`) y los finales de línea originales.
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
                if ch == '\n':
                    out.append(ch)
                i += 1
        elif state == 'STRING':
            out.append(ch)
            if ch == '\\' and i + 1 < n:
                out.append(text[i + 1])
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
                out.append(text[i + 1])
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
        base = Path(__file__).resolve().parent / 'app' / 'src' / 'main'

    if not base.exists():
        print(f'No existe la ruta: {base}')
        return 1

    modified = 0
    for root, _, files in os.walk(base):
        for file in files:
            path = Path(root) / file
            if file.endswith(('.kt', '.kts')):
                remove = remove_kotlin_comments
            elif file.endswith('.xml'):
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
