#!/usr/bin/env python3
"""
make_icon.py — готовит адаптивную иконку Android из вашего PNG.

Использование:
    python3 make_icon.py /путь/к/картинке.png

Что делает:
  1. Берёт ваш PNG (любого квадратного размера, например 1254x1254).
  2. Уменьшает до 66% холста (безопасная зона адаптивной иконки).
  3. Кладёт в центр прозрачного холста 432x432 (xxxhdpi foreground).
  4. Сохраняет как app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png
  5. Записывает ic_launcher.xml / ic_launcher_round.xml (adaptive-icon).
  6. Создаёт/дополняет colors.xml цветом фона.
"""

import sys
import os
from PIL import Image

RES = "app/src/main/res"
OUT_FOREGROUND = os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher_foreground.png")
ANYDPI = os.path.join(RES, "mipmap-anydpi-v26")

CANVAS = 432          # размер foreground xxxhdpi (108dp * 4)
SAFE_RATIO = 0.66     # рисунок занимает 66% холста (безопасная зона)

XML_TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
"""

def main():
    if len(sys.argv) < 2:
        print("Использование: python3 make_icon.py /путь/к/картинке.png")
        sys.exit(1)

    src_path = sys.argv[1]
    if not os.path.exists(src_path):
        print(f"Файл не найден: {src_path}")
        sys.exit(1)

    img = Image.open(src_path).convert("RGBA")

    # Квадратизируем (центральный кроп), на всякий случай
    side = min(img.size)
    left = (img.width - side) // 2
    top = (img.height - side) // 2
    img = img.crop((left, top, left + side, top + side))

    # Рисунок = 66% холста
    draw_size = int(CANVAS * SAFE_RATIO)
    img = img.resize((draw_size, draw_size), Image.LANCZOS)

    # Прозрачный холст + рисунок по центру
    canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    offset = (CANVAS - draw_size) // 2
    canvas.paste(img, (offset, offset), img)

    os.makedirs(os.path.dirname(OUT_FOREGROUND), exist_ok=True)
    canvas.save(OUT_FOREGROUND, "PNG")
    print(f"OK: {OUT_FOREGROUND}")

    os.makedirs(ANYDPI, exist_ok=True)
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        with open(os.path.join(ANYDPI, name), "w", encoding="utf-8") as f:
            f.write(XML_TEMPLATE)
        print(f"OK: {os.path.join(ANYDPI, name)}")

    colors_path = os.path.join(RES, "values", "colors.xml")
    os.makedirs(os.path.dirname(colors_path), exist_ok=True)
    if not os.path.exists(colors_path):
        with open(colors_path, "w", encoding="utf-8") as f:
            f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                    '    <color name="ic_launcher_background">#101014</color>\n</resources>\n')
        print(f"OK: {colors_path} (создан)")
    else:
        content = open(colors_path, encoding="utf-8").read()
        if "ic_launcher_background" not in content:
            content = content.replace("<resources>",
                '<resources>\n    <color name="ic_launcher_background">#101014</color>')
            with open(colors_path, "w", encoding="utf-8") as f:
                f.write(content)
            print(f"OK: {colors_path} (добавлен цвет)")
        else:
            print(f"OK: {colors_path} (цвет уже есть)")

    print("\nГотово! Дальше:")
    print('  find app/src/main/res/mipmap-* -name "ic_launcher*.png" ! -name "*foreground*" -delete')
    print("  ./gradlew assembleRelease")

if __name__ == "__main__":
    main()
