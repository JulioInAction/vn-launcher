"""Añade "hinting" a las fuentes de src/main/resources/fonts con ttfautohint.

Por qué: las fuentes de Google Fonts (Inter, Zen Kaku Gothic New) vienen SIN instrucciones de
hinting. En Linux no se nota, porque FreeType tiene un "autohinter" propio, pero Windows
(DirectWrite, que es lo que usa JavaFX allí) no lo tiene: sin hinting, los bordes horizontales de
las letras (arriba de la "a", la línea base...) caen entre dos píxeles y el texto pequeño se ve
borroso. ttfautohint calcula esas instrucciones y las mete en el archivo.

Uso (una sola vez, o al actualizar una fuente; el resultado se guarda en el repositorio):
    pip install ttfautohint-py
    python3 tools/hint_fonts.py

Las licencias OFL de estas fuentes no declaran "Reserved Font Name", así que la versión
modificada puede conservar el nombre (y sigue bajo la OFL).
"""
from pathlib import Path

import ttfautohint

FONTS = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "fonts"

for font in sorted(FONTS.glob("*.ttf")):
    data = font.read_bytes()
    # Si ya tiene instrucciones propias (tabla fpgm), no se vuelve a procesar
    if b"fpgm" in data[:12 + 16 * int.from_bytes(data[4:6], "big")]:
        print(f"{font.name}: ya tiene hinting")
        continue
    # windows_compatibility: tiene en cuenta usWinAscent/usWinDescent para que Windows no recorte
    # los acentos de las mayúsculas ni las letras que bajan de la línea
    hinted = ttfautohint.ttfautohint(in_buffer=data, windows_compatibility=True)
    font.write_bytes(hinted)
    print(f"{font.name}: {len(data) // 1024} KB -> {len(hinted) // 1024} KB")
