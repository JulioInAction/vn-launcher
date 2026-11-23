# Fuentes

Tipografías de Google Fonts incluidas en la aplicación (ver `docs/design.md`):

| Archivo | Familia en JavaFX | Uso |
|---|---|---|
| `Inter-Regular.ttf` | `Inter` | texto normal (400) |
| `Inter-Medium.ttf` | `Inter Medium` | etiquetas, botones (500) |
| `Inter-SemiBold.ttf` | `Inter SemiBold` | énfasis (600) |
| `ZenKakuGothicNew-Bold.ttf` | `Zen Kaku Gothic New` (bold) | títulos, nombres, navegación; incluye japonés |
| `ZenKakuGothicNew-Medium.ttf` | `Zen Kaku Gothic New Medium` | títulos y nombres originales en japonés (500) |

Ambas se distribuyen con la **SIL Open Font License 1.1**: `OFL-Inter.txt` y
`OFL-ZenKakuGothicNew.txt`. La OFL permite incluirlas en un programa siempre que se acompañen
de su licencia y no se vendan por separado.

## Hinting para Windows

Los archivos de Google Fonts vienen **sin hinting** (las instrucciones que ajustan cada letra a la
cuadrícula de píxeles). Linux lo disimula con su autohinter, pero en Windows JavaFX dibuja con
DirectWrite, que no lo tiene, y el texto pequeño salía borroso. Por eso estos `.ttf` son versiones
**modificadas**: se les ha añadido hinting con [ttfautohint](https://freetype.org/ttfautohint/)
mediante `tools/hint_fonts.py` (mismos glifos y nombres; solo se añaden las tablas `fpgm`,
`cvt ` y `prep`). Las licencias no declaran «Reserved Font Name», así que pueden conservar el
nombre, y siguen bajo la OFL.
