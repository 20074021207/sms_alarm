"""Generate launcher icon: SMS envelope + alarm bell style"""
from PIL import Image, ImageDraw
import os

BASE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(BASE, "app", "src", "main", "res")

SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

def draw_icon(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # Scale factor relative to 192px base
    s = size / 192.0

    # --- Background circle (deep blue gradient feel) ---
    # Outer circle
    margin = int(8 * s)
    d.ellipse([margin, margin, size - margin, size - margin],
              fill=(30, 80, 180, 255))
    # Inner lighter circle for gradient effect
    m2 = int(16 * s)
    d.ellipse([m2, m2, size - m2, size - m2],
              fill=(40, 100, 220, 255))

    # --- SMS Envelope (white, centered-left-bottom) ---
    env_l = int(30 * s)
    env_t = int(90 * s)
    env_r = int(120 * s)
    env_b = int(150 * s)
    env_cx = (env_l + env_r) // 2

    # Envelope body
    d.rectangle([env_l, env_t, env_r, env_b], fill=(255, 255, 255, 230))
    # Envelope flap (triangle)
    d.polygon([
        (env_l, env_t),
        (env_cx, int(120 * s)),
        (env_r, env_t)
    ], fill=(220, 230, 255, 200))
    # Envelope bottom lines
    d.polygon([
        (env_l, env_b),
        (env_cx, int((90 + 150) / 2 * s)),
        (env_r, env_b)
    ], fill=(200, 215, 245, 180))

    # SMS text indicator (small "SMS" dots on envelope)
    dot_y = int(118 * s)
    for dx in [-18, -6, 6, 18]:
        cx = env_cx + int(dx * s)
        d.ellipse([cx - int(3*s), dot_y - int(3*s), cx + int(3*s), dot_y + int(3*s)],
                  fill=(30, 80, 180, 200))

    # --- Alarm Bell (golden, top-right area) ---
    bell_cx = int(135 * s)
    bell_cy = int(70 * s)
    bell_w = int(32 * s)
    bell_h = int(38 * s)

    gold = (255, 200, 50, 255)
    dark_gold = (200, 150, 30, 255)

    # Bell top knob
    d.ellipse([bell_cx - int(6*s), bell_cy - bell_h//2 - int(10*s),
               bell_cx + int(6*s), bell_cy - bell_h//2 + int(2*s)],
              fill=gold)
    # Bell body (trapezoid shape)
    bell_top_l = bell_cx - int(14 * s)
    bell_top_r = bell_cx + int(14 * s)
    bell_bot_l = bell_cx - int(22 * s)
    bell_bot_r = bell_cx + int(22 * s)
    bell_top_y = bell_cy - bell_h // 2
    bell_bot_y = bell_cy + bell_h // 2
    d.polygon([
        (bell_top_l, bell_top_y),
        (bell_top_r, bell_top_y),
        (bell_bot_r, bell_bot_y),
        (bell_bot_l, bell_bot_y)
    ], fill=gold)
    # Bell rim (wider bottom)
    rim_h = int(6 * s)
    d.rectangle([bell_bot_l - int(4*s), bell_bot_y,
                 bell_bot_r + int(4*s), bell_bot_y + rim_h],
                fill=dark_gold)
    # Bell clapper
    d.ellipse([bell_cx - int(5*s), bell_bot_y + rim_h,
               bell_cx + int(5*s), bell_bot_y + rim_h + int(8*s)],
              fill=dark_gold)

    # --- Alarm rings (vibration lines) ---
    ring_color = (255, 230, 100, 180)
    for offset in [int(18 * s), int(24 * s)]:
        # Left arc
        d.arc([bell_cx - bell_w - offset, bell_cy - int(15*s),
               bell_cx - bell_w - offset + int(12*s), bell_cy + int(15*s)],
              start=-60, end=60, fill=ring_color, width=max(1, int(2*s)))
        # Right arc
        d.arc([bell_cx + bell_w + offset - int(12*s), bell_cy - int(15*s),
               bell_cx + bell_w + offset, bell_cy + int(15*s)],
              start=120, end=240, fill=ring_color, width=max(1, int(2*s)))

    return img


for folder, sz in SIZES.items():
    out_dir = os.path.join(RES, folder)
    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, "ic_launcher.png")
    img = draw_icon(sz)
    img.save(out_path, "PNG")
    print(f"Generated {out_path} ({sz}x{sz})")

# Also generate round icon
for folder, sz in SIZES.items():
    out_dir = os.path.join(RES, folder)
    out_path = os.path.join(out_dir, "ic_launcher_round.png")
    # Round icon is same as regular (already circular design)
    img = draw_icon(sz)
    img.save(out_path, "PNG")
    print(f"Generated {out_path} ({sz}x{sz})")

print("Done!")
