"""สร้างเท็กซ์เจอร์ pixel art 16x16 ของม็อดเงิน: python3 tools_make_textures.py"""
from PIL import Image
import os

OUT = "src/main/resources/assets/money/textures"

FONT = {  # ฟอนต์ 3x5
 '0':["111","101","101","101","111"], '1':["010","110","010","010","111"],
 '2':["111","001","111","100","111"], '3':["111","001","111","001","111"],
 '4':["101","101","111","001","001"], '5':["111","100","111","001","111"],
 '6':["111","100","111","101","111"], '7':["111","001","001","001","001"],
 '8':["111","101","111","101","111"], '9':["111","101","111","001","111"],
 'K':["101","101","110","101","101"],
}

def text(img, s, cx, cy, color):
    w = len(s) * 4 - 1
    x0 = cx - w // 2
    y0 = cy - 2
    for i, ch in enumerate(s):
        for ry, row in enumerate(FONT[ch]):
            for rx, bit in enumerate(row):
                if bit == '1':
                    img.putpixel((x0 + i * 4 + rx, y0 + ry), color)

def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)

def coin(name, label, base):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= 6.6:
                if d > 5.4:
                    c = shade(base, 0.55)          # ขอบเหรียญ
                elif x + y < 12:
                    c = shade(base, 1.18)          # ไฮไลต์ซ้ายบน
                else:
                    c = shade(base, 1.0)
                img.putpixel((x, y), c)
    text(img, label, 8, 8, shade(base, 0.4))
    img.save(f"{OUT}/item/{name}.png")

def note(name, label, base):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(3, 13):
        for x in range(0, 16):
            edge = x in (0, 15) or y in (3, 12)
            inner = x in (1, 14) or y in (4, 11)
            c = shade(base, 0.5) if edge else (shade(base, 1.25) if inner else shade(base, 1.0))
            img.putpixel((x, y), c)
    # ลายมุม
    for (x, y) in [(2, 5), (13, 5), (2, 10), (13, 10)]:
        img.putpixel((x, y), shade(base, 0.7))
    text(img, label, 8, 8, (255, 255, 255, 255))
    img.save(f"{OUT}/item/{name}.png")

coin("coin_1", "1", (205, 127, 50))     # ทองแดง
coin("coin_2", "2", (200, 205, 215))    # เงิน
coin("coin_5", "5", (240, 195, 50))     # ทอง
coin("coin_10", "10", (60, 110, 220))   # น้ำเงิน
note("note_20", "20", (60, 160, 80))    # เขียว
note("note_50", "50", (70, 160, 220))   # ฟ้า
note("note_100", "100", (200, 60, 60))  # แดง
note("note_500", "500", (140, 80, 190)) # ม่วง
note("note_1000", "1K", (225, 180, 40)) # ทอง

# ---- ATM ----
def atm_side():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    body, dark = (120, 125, 135, 255), (70, 74, 82, 255)
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), dark if x in (0, 15) or y in (0, 15) else body)
    for y in range(2, 7):                                  # จอ
        for x in range(2, 14):
            img.putpixel((x, y), (20, 40, 25, 255) if (x in (2, 13) or y in (2, 6)) else (40, 170, 70, 255))
    for x in range(4, 12):                                 # ข้อความในจอ
        if x % 2 == 0:
            img.putpixel((x, 4), (190, 255, 200, 255))
    for ky in range(8, 13):                                # ปุ่มกด
        for kx in (3, 6, 9, 12):
            if ky % 2 == 0:
                img.putpixel((kx, ky), (230, 230, 235, 255))
                img.putpixel((kx - 1, ky), (200, 200, 205, 255))
    for x in range(3, 13):                                 # ช่องคืนเงิน
        img.putpixel((x, 14), (15, 15, 18, 255))
    img.save(f"{OUT}/block/atm_side.png")

def plain(name, base, border):
    img = Image.new("RGBA", (16, 16), base)
    for i in range(16):
        for (x, y) in [(i, 0), (i, 15), (0, i), (15, i)]:
            img.putpixel((x, y), border)
    if name == "atm_top":
        for x in range(4, 12):
            for y in range(7, 9):
                img.putpixel((x, y), (60, 64, 72, 255))   # ช่องใส่บัตร/ระบายอากาศ
    img.save(f"{OUT}/block/{name}.png")

atm_side()
plain("atm_top", (140, 145, 155, 255), (90, 94, 102, 255))
plain("atm_bottom", (80, 84, 92, 255), (55, 58, 64, 255))
print("textures ok")

# ---- กระเป๋าสตางค์ ----
def wallet():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    leather, dark, light = (140, 90, 50, 255), (85, 52, 28, 255), (170, 115, 70, 255)
    for y in range(4, 13):
        for x in range(1, 15):
            edge = x in (1, 14) or y in (4, 12)
            img.putpixel((x, y), dark if edge else (light if y == 5 else leather))
    for x in range(3, 13):                       # ธนบัตรโผล่ปากกระเป๋า
        img.putpixel((x, 3), (60, 160, 80, 255))
        img.putpixel((x, 2), (90, 190, 110, 255)) if 4 <= x <= 11 else None
    for y in (7, 8, 9):                          # ตะขอทอง
        for x in (7, 8):
            img.putpixel((x, y), (240, 195, 50, 255))
    for x in range(2, 14):                       # ตะเข็บ
        if x % 2 == 0:
            img.putpixel((x, 11), (200, 150, 100, 255))
    img.save(f"{OUT}/item/wallet.png")

wallet()
print("wallet ok")
