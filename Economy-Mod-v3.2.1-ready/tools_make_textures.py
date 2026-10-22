"""สร้างเท็กซ์เจอร์ pixel art 16x16 ของม็อดเงินไทย ให้สมจริงตามลักษณะจริง:
- เหรียญ 1 บาท: โลหะสีเงิน (Nickel-plated steel)
- เหรียญ 2 บาท: โลหะสีทองเหลือง (Aluminium-bronze)
- เหรียญ 5 บาท: โลหะสีเงินขอบทองแดง (Cupronickel-clad copper)
- เหรียญ 10 บาท: เหรียญสองกษัตริย์ (Bimetallic) วงนอกสีเงิน วงในสีทองเหลือง
- ธนบัตร 20: สีเขียว (Green) + แถบฟอยล์กันปลอม
- ธนบัตร 50: สีฟ้า (Blue) + แถบฟอยล์กันปลอม
- ธนบัตร 100: สีแดง (Red) + แถบฟอยล์กันปลอม
- ธนบัตร 500: สีม่วง (Purple) + แถบฟอยล์กันปลอม
- ธนบัตร 1,000: สีน้ำตาลอมเทา (Warm Taupe / Brown-Grey) + แถบทองโฮโลแกรม (Gold Holographic Foil)
"""
from PIL import Image
import os

OUT = "src/main/resources/assets/money/textures"
os.makedirs(f"{OUT}/item", exist_ok=True)
os.makedirs(f"{OUT}/block", exist_ok=True)

FONT = {  # ฟอนต์ 3x5
 '0':["111","101","101","101","111"],
 '1':["010","110","010","010","111"],
 '2':["111","001","111","100","111"],
 '3':["111","001","111","001","111"],
 '4':["101","101","111","001","001"],
 '5':["111","100","111","001","111"],
 '6':["111","100","111","101","111"],
 '7':["111","001","001","001","001"],
 '8':["111","101","111","101","111"],
 '9':["111","101","111","001","111"],
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

# เหรียญธรรมดา (สีเดียว)
def coin(name, label, base, radius, font_color=None):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= radius:
                if d > radius - 1.2:
                    c = shade(base, 0.58)          # ขอบเหรียญ
                elif x + y < 13:
                    c = shade(base, 1.22)          # แสงสะท้อนซ้ายบน
                else:
                    c = shade(base, 0.95)
                img.putpixel((x, y), c)
    fc = font_color if font_color else shade(base, 0.42)
    text(img, label, 8, 8, fc)
    img.save(f"{OUT}/item/{name}.png")

# เหรียญ 10 สองกษัตริย์ (Bimetallic: วงนอกเงิน วงในทองเหลือง)
def coin_10_bimetallic():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cx = cy = 7.5
    outer_silver = (200, 205, 215)
    inner_brass = (235, 185, 45)
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= 6.6:
                if d > 5.4:
                    c = shade(outer_silver, 0.55)      # ขอบนอกสุด
                elif d > 3.8:
                    # วงแหวนเงิน
                    c = shade(outer_silver, 1.20 if x + y < 13 else 0.95)
                elif d > 3.2:
                    # รอยต่อสองกษัตริย์
                    c = shade(inner_brass, 0.70)
                else:
                    # แกนในสีทองเหลือง
                    c = shade(inner_brass, 1.15 if x + y < 13 else 0.95)
                img.putpixel((x, y), c)
    # ตัวเลข 10 บนแกนทองเหลือง
    text(img, "10", 8, 8, (120, 80, 15, 255))
    img.save(f"{OUT}/item/coin_10.png")

# เหรียญตามลักษณะจริงของไทย
coin("coin_1", "1", (200, 205, 212), 5.8)            # 1 บาท: โลหะเงิน นิเกิล
coin("coin_2", "2", (230, 185, 45), 6.1)             # 2 บาท: โลหะทองเหลือง อลูมิเนียมบรอนซ์
coin("coin_5", "5", (195, 200, 208), 6.4, (85, 90, 100, 255))  # 5 บาท: โลหะเงิน คิวโปรนิกเกิล
coin_10_bimetallic()                                  # 10 บาท: สองกษัตริย์ (ขอบเงิน-ไส้ทอง)

# ธนบัตร (Note) พร้อมแถบฟอยล์โฮโลแกรมกันปลอม
def note(name, label, base, foil_color=(235, 235, 245)):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(3, 13):
        for x in range(0, 16):
            edge = x in (0, 15) or y in (3, 12)
            inner = x in (1, 14) or y in (4, 11)
            c = shade(base, 0.48) if edge else (shade(base, 1.22) if inner else shade(base, 1.0))
            img.putpixel((x, y), c)
    # แถบฟอยล์ / Security Thread แนวตั้งที่ x = 4
    for y in range(3, 13):
        img.putpixel((4, y), foil_color + (255,))
    # ลวดลายมุมทั้ง 4
    for (x, y) in [(2, 5), (13, 5), (2, 10), (13, 10)]:
        img.putpixel((x, y), shade(base, 0.65))
    text(img, label, 9, 8, (255, 255, 255, 255))
    img.save(f"{OUT}/item/{name}.png")

note("note_20", "20", (55, 155, 75), (200, 245, 210))          # เขียว + แถบฟอยล์
note("note_50", "50", (45, 145, 220), (190, 235, 255))         # ฟ้า + แถบฟอยล์
note("note_100", "100", (205, 55, 55), (255, 210, 210))        # แดง + แถบฟอยล์
note("note_500", "500", (135, 75, 190), (230, 205, 255))       # ม่วง + แถบฟอยล์
# 1,000 บาท: สีน้ำตาลเทา (Warm Taupe / Brown-Grey) + แถบทองโฮโลแกรม (Gold Foil)
note("note_1000", "1K", (165, 145, 125), (245, 205, 60))

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
print("All realistic Thai money textures generated successfully!")
