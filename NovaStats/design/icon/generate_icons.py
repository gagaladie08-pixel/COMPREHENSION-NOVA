#!/usr/bin/env python3
"""
Générateur de l'icône NovaStats (cahier des charges « Icône ») :
  • N stylisé + onde sonore intégrée, dans la zone de sécurité (cercle 66/108 dp)
  • 15 variantes (une par thème) : dégradé du N = primary → secondary → glowSecondary,
    fond = background → surface (vertical), onde = accent à 70 %
  • Adaptive icon (foreground / background / monochrome) en vector drawables → aucune PNG dans l'APK
  • Icône de notification (N blanc), vecteur du splash + animated-vector (scale overshoot + pulsation de l'onde)
  • PNG 512×512 Play Store par thème (rendu pur Python, sans dépendance)

Usage : python3 generate_icons.py   (depuis n'importe où ; chemins relatifs au dépôt)
"""
import math
import os
import re
import struct
import zlib

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")
THEMES_KT = os.path.join(ROOT, "app", "src", "main", "java", "com", "novastats", "app", "ui", "theme", "NovaThemes.kt")
OUT_PNG = os.path.dirname(os.path.abspath(__file__))

# ---------------------------------------------------------------- géométrie (viewport 108, zone sûre = cercle r 33 centré 54,54)
# N : barres verticales x 31..40 et 68..77, y 32..76 ; diagonale = bande entre (40,32)→(68,64) et (40,44)→(68,76)
N_PATH = "M31,76 V32 H40 L68,64 V32 H77 V76 H68 L40,44 V76 Z"
N_POLY = [(31, 76), (31, 32), (40, 32), (68, 64), (68, 32), (77, 32), (77, 76), (68, 76), (40, 44), (40, 76)]
# Onde sonore : 7 barres arrondies centrées sur y = 54 (hauteurs 6 → 32 → 6), largeur 4
WAVE_X = [30, 38, 46, 54, 62, 70, 78]
WAVE_H = [6, 14, 24, 32, 24, 14, 6]
WAVE_W = 4.0
WAVE_PATH = " ".join(f"M{x},{54 - h / 2:g} V{54 + h / 2:g}" for x, h in zip(WAVE_X, WAVE_H))
GRAD = dict(x0=31, y0=32, x1=77, y1=76)  # axe du dégradé du N (diagonale haut-gauche → bas-droite)

# ---------------------------------------------------------------- thèmes
def parse_themes():
    src = open(THEMES_KT, encoding="utf-8").read()
    pat = re.compile(r'NovaTheme\(\s*"([a-z0-9_]+)",\s*"[^"]*",\s*"([^"]+)",\s*((?:hex\("#[0-9A-Fa-f]{6}"\),?\s*){8})')
    themes = []
    for m in pat.finditer(src):
        cols = re.findall(r'#([0-9A-Fa-f]{6})', m.group(3))
        primary, secondary, glow, background, surface, _text, _text2, accent = ["#" + c.upper() for c in cols]
        themes.append(dict(id=m.group(1), name=m.group(2), primary=primary, secondary=secondary, glow=glow, background=background, surface=surface, accent=accent))
    assert len(themes) == 15, f"{len(themes)} thèmes trouvés"
    return themes

# ---------------------------------------------------------------- XML
HEADER = '<?xml version="1.0" encoding="utf-8"?>\n'
GEN = "<!-- Généré par design/icon/generate_icons.py — ne pas éditer à la main -->\n"

def fg_xml(t):
    return f'''{HEADER}{GEN}<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <!-- N stylisé — dégradé {t['primary']} → {t['secondary']} → {t['glow']} ({t['name']}) -->
    <path android:pathData="{N_PATH}">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="{GRAD['x0']}" android:startY="{GRAD['y0']}" android:endX="{GRAD['x1']}" android:endY="{GRAD['y1']}">
                <item android:offset="0" android:color="{t['primary']}" />
                <item android:offset="0.5" android:color="{t['secondary']}" />
                <item android:offset="1" android:color="{t['glow']}" />
            </gradient>
        </aapt:attr>
    </path>
    <!-- Onde sonore intégrée — accent à 70 % -->
    <path android:pathData="{WAVE_PATH}" android:strokeColor="{t['accent']}" android:strokeWidth="{WAVE_W:g}" android:strokeLineCap="round" android:strokeAlpha="0.7" />
</vector>
'''

def bg_xml(t):
    return f'''{HEADER}{GEN}<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <!-- Fond : dégradé vertical {t['background']} → {t['surface']} ({t['name']}) -->
    <path android:pathData="M0,0 H108 V108 H0 Z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="54" android:startY="0" android:endX="54" android:endY="108">
                <item android:offset="0" android:color="{t['background']}" />
                <item android:offset="1" android:color="{t['surface']}" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
'''

def adaptive_xml(t):
    return f'''{HEADER}{GEN}<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_bg_{t['id']}" />
    <foreground android:drawable="@drawable/ic_launcher_fg_{t['id']}" />
    <monochrome android:drawable="@drawable/ic_launcher_mono" />
</adaptive-icon>
'''

MONO_XML = f'''{HEADER}{GEN}<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <!-- Icône monochrome (thème système / notifications) : N blanc sur transparent -->
    <path android:fillColor="#FFFFFFFF" android:pathData="{N_PATH}" />
</vector>
'''

NOTIF_XML = f'''{HEADER}{GEN}<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="108" android:viewportHeight="108">
    <!-- Petite icône de notification : N blanc (le système applique la teinte) -->
    <group android:scaleX="1.6" android:scaleY="1.6" android:pivotX="54" android:pivotY="54">
        <path android:fillColor="#FFFFFFFF" android:pathData="{N_PATH}" />
    </group>
</vector>
'''

def splash_vector_xml(t):
    bars = "\n".join(
        f'        <group android:name="bar{i}" android:pivotX="{x}" android:pivotY="54">\n'
        f'            <path android:pathData="M{x},{54 - h / 2:g} V{54 + h / 2:g}" android:strokeColor="{t["accent"]}" android:strokeWidth="{WAVE_W:g}" android:strokeLineCap="round" android:strokeAlpha="0.7" />\n'
        f'        </group>'
        for i, (x, h) in enumerate(zip(WAVE_X, WAVE_H))
    )
    return f'''{HEADER}{GEN}<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <!-- Icône du splash screen (Android 12+) : N + onde, groupes nommés pour l'animated-vector -->
    <group android:name="n" android:pivotX="54" android:pivotY="54">
        <path android:pathData="{N_PATH}">
            <aapt:attr name="android:fillColor">
                <gradient android:type="linear" android:startX="{GRAD['x0']}" android:startY="{GRAD['y0']}" android:endX="{GRAD['x1']}" android:endY="{GRAD['y1']}">
                    <item android:offset="0" android:color="{t['primary']}" />
                    <item android:offset="0.5" android:color="{t['secondary']}" />
                    <item android:offset="1" android:color="{t['glow']}" />
                </gradient>
            </aapt:attr>
        </path>
    </group>
    <group android:name="wave" android:pivotX="54" android:pivotY="54">
{bars}
    </group>
</vector>
'''

SPLASH_AVD_XML = f'''{HEADER}{GEN}<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:drawable="@drawable/ic_splash_vector">
    <!-- 1. Le N apparaît de l'intérieur vers l'extérieur : scale 0 → 1 avec overshoot -->
    <target android:name="n">
        <aapt:attr name="android:animation">
            <set>
                <objectAnimator android:propertyName="scaleX" android:valueFrom="0" android:valueTo="1" android:duration="550" android:interpolator="@android:anim/overshoot_interpolator" />
                <objectAnimator android:propertyName="scaleY" android:valueFrom="0" android:valueTo="1" android:duration="550" android:interpolator="@android:anim/overshoot_interpolator" />
            </set>
        </aapt:attr>
    </target>
    <!-- 2. L'onde sonore pulse une fois (après l'apparition du N) -->
    <target android:name="wave">
        <aapt:attr name="android:animation">
            <set android:ordering="sequentially">
                <objectAnimator android:propertyName="scaleY" android:valueFrom="0" android:valueTo="0" android:duration="350" />
                <objectAnimator android:propertyName="scaleY" android:valueFrom="0" android:valueTo="1.35" android:duration="300" android:interpolator="@android:anim/decelerate_interpolator" />
                <objectAnimator android:propertyName="scaleY" android:valueFrom="1.35" android:valueTo="1" android:duration="250" android:interpolator="@android:anim/accelerate_decelerate_interpolator" />
            </set>
        </aapt:attr>
    </target>
</animated-vector>
'''

# ---------------------------------------------------------------- rendu PNG 512 (Play Store) — pur Python
SIZE = 512
SS = 3  # sur-échantillonnage par axe

def point_in_poly(x, y, poly):
    inside = False
    n = len(poly)
    j = n - 1
    for i in range(n):
        xi, yi = poly[i]; xj, yj = poly[j]
        if (yi > y) != (yj > y):
            xcross = (xj - xi) * (y - yi) / (yj - yi) + xi
            if x < xcross:
                inside = not inside
        j = i
    return inside

def capsule_dist(px, py, x, y0, y1):
    cy = min(max(py, y0), y1)
    return math.hypot(px - x, py - cy)

def rounded_square_inside(x, y, r):
    # carré 0..108, coins arrondis de rayon r
    cx = min(max(x, r), 108 - r); cy = min(max(y, r), 108 - r)
    return math.hypot(x - cx, y - cy) <= r

def compute_masks():
    """Masques indépendants du thème : couverture fond / N / onde + paramètre t du dégradé du N."""
    bg, nn, wv, tt = [], [], [], []
    inv = 1.0 / (SS * SS)
    gx, gy = GRAD["x1"] - GRAD["x0"], GRAD["y1"] - GRAD["y0"]
    glen2 = gx * gx + gy * gy
    for py in range(SIZE):
        for px in range(SIZE):
            cb = cn = cw = 0
            for sy in range(SS):
                for sx in range(SS):
                    x = (px + (sx + 0.5) / SS) * 108.0 / SIZE
                    y = (py + (sy + 0.5) / SS) * 108.0 / SIZE
                    if rounded_square_inside(x, y, 24):
                        cb += 1
                    if point_in_poly(x, y, N_POLY):
                        cn += 1
                    for bx, bh in zip(WAVE_X, WAVE_H):
                        if capsule_dist(x, y, bx, 54 - bh / 2, 54 + bh / 2) <= WAVE_W / 2:
                            cw += 1
                            break
            bg.append(cb * inv); nn.append(cn * inv); wv.append(cw * inv)
            x = (px + 0.5) * 108.0 / SIZE; y = (py + 0.5) * 108.0 / SIZE
            t = ((x - GRAD["x0"]) * gx + (y - GRAD["y0"]) * gy) / glen2
            tt.append(min(max(t, 0.0), 1.0))
    return bg, nn, wv, tt

def hex_rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))

def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))

def n_gradient(t, c0, c1, c2):
    return lerp(c0, c1, t * 2) if t < 0.5 else lerp(c1, c2, (t - 0.5) * 2)

def write_png(path, rgba_rows):
    raw = b"".join(b"\x00" + bytes(row) for row in rgba_rows)
    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)

def render_png(t, masks):
    bg, nn, wv, tt = masks
    c_bg0, c_bg1 = hex_rgb(t["background"]), hex_rgb(t["surface"])
    p, s, g = hex_rgb(t["primary"]), hex_rgb(t["secondary"]), hex_rgb(t["glow"])
    acc = hex_rgb(t["accent"])
    rows = []
    i = 0
    for py in range(SIZE):
        row = bytearray()
        fy = (py + 0.5) / SIZE
        base = lerp(c_bg0, c_bg1, fy)
        for px in range(SIZE):
            a = bg[i]
            col = base
            if nn[i] > 0:
                col = lerp(col, n_gradient(tt[i], p, s, g), nn[i])
            if wv[i] > 0:
                col = lerp(col, acc, 0.7 * wv[i])
            row += bytes((int(col[0] + 0.5), int(col[1] + 0.5), int(col[2] + 0.5), int(a * 255 + 0.5)))
            i += 1
        rows.append(row)
    write_png(os.path.join(OUT_PNG, f"playstore_{t['id']}.png"), rows)

# ---------------------------------------------------------------- main
def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

def main(render=True):
    themes = parse_themes()
    for t in themes:
        write(os.path.join(RES, "drawable", f"ic_launcher_fg_{t['id']}.xml"), fg_xml(t))
        write(os.path.join(RES, "drawable", f"ic_launcher_bg_{t['id']}.xml"), bg_xml(t))
        write(os.path.join(RES, "mipmap-anydpi-v26", f"ic_launcher_{t['id']}.xml"), adaptive_xml(t))
    default = themes[0]
    write(os.path.join(RES, "mipmap-anydpi-v26", "ic_launcher.xml"), adaptive_xml(default))
    write(os.path.join(RES, "drawable", "ic_launcher_mono.xml"), MONO_XML)
    write(os.path.join(RES, "drawable", "ic_notification.xml"), NOTIF_XML)
    write(os.path.join(RES, "drawable", "ic_splash_vector.xml"), splash_vector_xml(default))
    write(os.path.join(RES, "drawable-v31", "ic_splash_anim.xml"), SPLASH_AVD_XML)
    # Snippet manifeste (activity-alias)
    aliases = "\n".join(
        f'        <activity-alias android:name=".icon.{t["id"]}" android:targetActivity=".MainActivity" android:exported="true"\n'
        f'            android:icon="@mipmap/ic_launcher_{t["id"]}" android:label="@string/app_name" android:enabled="{"true" if t is default else "false"}">\n'
        f'            <intent-filter>\n                <action android:name="android.intent.action.MAIN" />\n                <category android:name="android.intent.category.LAUNCHER" />\n            </intent-filter>\n        </activity-alias>'
        for t in themes
    )
    write(os.path.join(OUT_PNG, "manifest_aliases.xml"), aliases + "\n")
    print(f"{len(themes)} thèmes → vecteurs écrits dans {RES}")
    if render:
        print("Rendu des masques 512×512…")
        masks = compute_masks()
        for t in themes:
            render_png(t, masks)
            print("  PNG", t["id"])

if __name__ == "__main__":
    import sys
    main(render="--no-png" not in sys.argv)
