#!/usr/bin/env python3
"""
Générateur de l'icône NovaStats (cahier des charges « Icône ») :
  • N stylisé + onde sonore intégrée, dans la zone de sécurité (cercle 66/108 dp)
  • 15 variantes (une par thème) : dégradé du N = primary → secondary → glowSecondary,
    fond = background → surface (vertical), onde = accent à 70 %
  • Adaptive icon (foreground / background / monochrome) en vector drawables → aucune PNG dans l'APK
  • Icône de notification (N blanc), vecteur du splash + animated-vector (scale overshoot + pulsation de l'onde)
  • 15 compositions UNIQUES (géométrie du N, onde et motif de fond propres à chaque univers ; Survivor = fierté queer)

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
    pat = re.compile(r'NovaTheme\(\s*"([a-z0-9_]+)",\s*"[^"]*",\s*"([^"]+)",\s*(?://[^\n]*\n\s*)*((?:hex\("#[0-9A-Fa-f]{6}"\),?\s*){8})')
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

# ---------------------------------------------------------------- 15 compositions uniques (une par univers)
# Règles : pas de texte, pas de particules ; le N + une onde restent dans le cercle de sécurité (r 33 autour de 54,54).
# Chaque thème a sa propre géométrie de N, sa propre « onde » et son propre motif de fond.
def P(d, fill=None, stroke=None, sw=None, alpha=None, cap=None, join=None, salpha=None):
    a = f' android:fillColor="{fill}"' if fill else ' android:fillColor="#00000000"'
    if stroke: a += f' android:strokeColor="{stroke}" android:strokeWidth="{sw:g}"'
    if alpha is not None: a += f' android:fillAlpha="{alpha:g}"'
    if salpha is not None: a += f' android:strokeAlpha="{salpha:g}"'
    if cap: a += f' android:strokeLineCap="{cap}"'
    if join: a += f' android:strokeLineJoin="{join}"'
    return f'    <path android:pathData="{d}"{a} />'

def G(d, stops, x0, y0, x1, y1, stroke=None, sw=None, alpha=None, radial=False):
    items = "".join(f'<item android:offset="{o:g}" android:color="{c}" />' for o, c in stops)
    g = (f'<gradient android:type="radial" android:centerX="{x0}" android:centerY="{y0}" android:gradientRadius="{x1}">' if radial
         else f'<gradient android:type="linear" android:startX="{x0}" android:startY="{y0}" android:endX="{x1}" android:endY="{y1}">')
    extra = f' android:strokeColor="{stroke}" android:strokeWidth="{sw:g}"' if stroke else ""
    if alpha is not None: extra += f' android:fillAlpha="{alpha:g}"'
    return f'    <path android:pathData="{d}"{extra}><aapt:attr name="android:fillColor">{g}{items}</gradient></aapt:attr></path>'

def CLIP(d, inner):
    return f'    <group>\n        <clip-path android:pathData="{d}" />\n' + "\n".join("    " + l for l in inner) + "\n    </group>"

def poly(pts):
    return "M" + " L".join(f"{x:g},{y:g}" for x, y in pts) + " Z"

def n_skew(k):  # N italique : le haut décalé de k vers la droite
    return poly([(x + (76 - y) / 44 * k, y) for x, y in N_POLY])

def n_stroke(w):  # N « tube » : tracé ouvert, à utiliser en stroke
    return "M35,73 V35 L73,73 V35"

def circle(cx, cy, r):
    return f"M{cx - r:g},{cy:g} a{r:g},{r:g} 0 1,0 {2 * r:g},0 a{r:g},{r:g} 0 1,0 {-2 * r:g},0 Z"

def ring(cx, cy, r):  # arc fermé pour stroke
    return circle(cx, cy, r)

def ngrad(t, d=N_PATH, **kw):
    return G(d, [(0, t['primary']), (0.5, t['secondary']), (1, t['glow'])], GRAD['x0'], GRAD['y0'], GRAD['x1'], GRAD['y1'], **kw)

def wave_bars(color, xs=WAVE_X, hs=WAVE_H, w=WAVE_W, alpha=0.7, y=54):
    return P(" ".join(f"M{x},{y - h / 2:g} V{y + h / 2:g}" for x, h in zip(xs, hs)), stroke=color, sw=w, cap="round", salpha=alpha)

def sine(y0, amp, x0=24, x1=84, periods=2.0, steps=24):
    pts = []
    for i in range(steps + 1):
        x = x0 + (x1 - x0) * i / steps
        pts.append((x, y0 + amp * math.sin(2 * math.pi * periods * i / steps)))
    return "M" + " L".join(f"{x:.1f},{y:.1f}" for x, y in pts)

def design(t):
    """Renvoie (bg_elements, fg_elements) pour le thème t."""
    i, p, sc, g, bg, sf, acc = t['id'], t['primary'], t['secondary'], t['glow'], t['background'], t['surface'], t['accent']
    BG, FG = [], []
    if i == "cyber_nova":   # HUD : grille, crochets d'angle, N chanfreiné, onde = lignes de balayage
        BG.append(G("M0,0 H108 V108 H0 Z", [(0, bg), (1, sf)], 54, 0, 54, 108))
        BG.append(P(" ".join(f"M{x},0 V108" for x in range(12, 108, 12)) + " " + " ".join(f"M0,{y} H108" for y in range(12, 108, 12)), stroke=sc, sw=0.6, salpha=0.18))
        BG.append(G(circle(54, 54, 40), [(0, sc), (1, "#00000000")], 54, 54, 40, 0, radial=True, alpha=0.25))
        FG.append(P("M30,38 V30 H38 M70,30 H78 V38 M78,70 V78 H70 M38,78 H30 V70", stroke=acc, sw=1.6, cap="square", salpha=0.9))
        FG.append(ngrad(t, poly([(31, 76), (31, 35), (34, 32), (40, 32), (68, 64), (68, 35), (71, 32), (77, 32), (77, 73), (74, 76), (68, 76), (40, 44), (40, 73), (37, 76)])))
        FG.append(P("M31,52 H77 M31,58 H77", stroke=acc, sw=1.2, salpha=0.55))
        FG.append(P("M44,64 H64", stroke=acc, sw=2.2, cap="round", salpha=0.8))
    elif i == "neon_disco":  # Tube néon : N en trait lumineux, boule disco = anneaux et facettes
        BG.append(G("M0,0 H108 V108 H0 Z", [(0, sf), (1, bg)], 0, 0, 108, 108))
        BG.append(P(" ".join(f"M{x},{y} h5 v5 h-5 Z" for x in range(2, 108, 9) for y in range(2, 108, 9)), fill=sc, alpha=0.08))
        FG.append(P(ring(54, 54, 30), stroke=sc, sw=1.2, salpha=0.6))
        FG.append(P(ring(54, 54, 30), stroke=g, sw=5, salpha=0.12))
        FG.append(P(n_stroke(9), stroke=p, sw=11, cap="round", join="round", salpha=0.35))
        FG.append(P(n_stroke(9), stroke=p, sw=6, cap="round", join="round"))
        FG.append(P(n_stroke(9), stroke="#FFFFFF", sw=1.6, cap="round", join="round", salpha=0.85))
        FG.append(wave_bars(sc, xs=[44, 49, 54, 59, 64], hs=[3, 6, 9, 6, 3], w=2.4, y=82, alpha=0.9))
    elif i == "villain_era":  # Sombre : N massif en biseau, entaille rouge, onde = électrocardiogramme
        BG.append(G("M0,0 H108 V108 H0 Z", [(0, bg), (1, sf)], 0, 0, 0, 108))
        BG.append(G("M0,108 L108,0 V40 L40,108 Z", [(0, p), (1, "#00000000")], 0, 108, 108, 0, alpha=0.18))
        FG.append(P(N_PATH, fill="#000000", alpha=0.6))
        FG.append(G("M31,76 V32 H40 L68,64 V32 H77 V76 H68 L40,44 V76 Z", [(0, "#F2F2F2"), (0.55, "#9A9A9A"), (1, "#3A3A3A")], 31, 32, 77, 76))
        FG.append(P("M26,30 L82,78", stroke=p, sw=2.2, cap="round"))
        FG.append(P("M30,82 H44 L48,77 L52,88 L56,79 L60,82 H78", stroke=p, sw=1.8, cap="round", join="round", salpha=0.9))
    elif i == "slay_queen":   # Royauté : couronne à trois pointes au-dessus du N doré, filet d'or
        BG.append(G("M0,0 H108 V108 H0 Z", [(0, sf), (1, bg)], 54, 0, 54, 108))
        BG.append(P(ring(54, 54, 36), stroke=sc, sw=0.8, salpha=0.35))
        FG.append(G("M33,34 L41,25 L48,31 L54,22 L60,31 L67,25 L75,34 Z", [(0, sc), (1, g)], 33, 22, 75, 34))
        FG.append(P(circle(41, 24, 2) + circle(54, 21, 2.4) + circle(67, 24, 2), fill=g))
        FG.append(ngrad(t, poly([(31, 80), (31, 37), (40, 37), (68, 69), (68, 37), (77, 37), (77, 80), (68, 80), (40, 49), (40, 80)])))
        FG.append(wave_bars(sc, xs=[46, 50, 54, 58, 62], hs=[2, 4, 7, 4, 2], w=2, y=85, alpha=0.9))
    elif i == "pink_y2k":     # Bubblegum : N en traits ronds et épais, bulles, reflet chromé
        BG.append(G("M0,0 H108 V108 H0 Z", [(0, sf), (0.5, bg), (1, sf)], 0, 0, 108, 108))
        BG.append(P("M0,30 C30,20 70,40 108,26 V36 C70,50 30,30 0,42 Z", fill="#FFFFFF", alpha=0.18))
        FG.append(P(n_stroke(9), stroke=g, sw=15, cap="round", join="round", salpha=0.4))
        FG.append(G(n_stroke(9), [(0, p), (1, sc)], 35, 35, 73, 73, stroke=p, sw=10))
        FG.append(P(n_stroke(9), stroke=sc, sw=10, cap="round", join="round"))
        FG.append(P("M39,38 L52,51", stroke="#FFFFFF", sw=2.4, cap="round", salpha=0.7))
        FG.append(P(circle(80, 36, 4) + circle(86, 46, 2.5) + circle(28, 72, 3), fill="#FFFFFF", alpha=0.75))
        FG.append(wave_bars("#FFFFFF", xs=[46, 50, 54, 58, 62], hs=[3, 6, 9, 6, 3], w=2.6, y=82, alpha=0.8))
    elif i == "velvet_stage": # Cabaret : rideaux latéraux, projecteur, rampe de lumières
        BG.append(G("M0,0 H108 V108 H0 Z", [(0, sf), (1, bg)], 54, 0, 54, 108))
        BG.append(G("M54,0 L14,108 H94 Z", [(0, sc), (1, "#00000000")], 54, 0, 54, 108, alpha=0.22))
        BG.append(G("M0,0 H26 C18,30 22,70 12,108 H0 Z", [(0, p), (1, bg)], 0, 54, 26, 54))
        BG.append(G("M108,0 H82 C90,30 86,70 96,108 H108 Z", [(0, p), (1, bg)], 108, 54, 82, 54))
        FG.append(G(N_PATH, [(0, "#FFF3C4"), (0.45, sc), (1, "#8A6508")], 31, 32, 77, 76))
        FG.append(P(N_PATH, stroke=g, sw=1, salpha=0.5))
        FG.append(P(circle(36, 82, 1.8) + circle(45, 84, 1.8) + circle(54, 85, 1.8) + circle(63, 84, 1.8) + circle(72, 82, 1.8), fill=acc))
    elif i == "pink_venom":   # Rock : rayures diagonales, N tranchant, éclair, goutte de venin
        BG.append(G("M0,0 H108 V108 H0 Z", [(0, bg), (1, sf)], 0, 0, 108, 108))
        BG.append(P(" ".join(f"M{x},108 L{x + 60},0 h8 L{x + 8},108 Z" for x in range(-60, 108, 24)), fill=p, alpha=0.12))
        FG.append(ngrad(t, poly([(31, 76), (31, 32), (40, 32), (68, 60), (68, 32), (77, 32), (77, 76), (70, 76), (40, 46), (40, 76)])))
        FG.append(P("M68,66 C68,70 72,72 72,76 C72,79 69,80 67,78 C65,76 68,70 68,66 Z", fill=p))
        FG.append(P("M26,82 L40,82 L37,86 L50,79 L46,87 L58,83 L80,83", stroke=acc, sw=2, cap="round", join="round", salpha=0.95))
    elif i == "cloud_nine":   # Pastel : N arrondi posé sur un nuage, vague douce
        BG.append(G("M0,0 H108 V108 H0 Z", [(0, sf), (1, bg)], 54, 0, 54, 108))
        BG.append(P(circle(84, 24, 10), fill="#FFFFFF", alpha=0.35))
        FG.append(P("M26,82 C22,82 22,72 30,72 C30,64 42,62 46,70 C50,64 62,64 64,72 C72,70 78,78 72,82 Z", fill="#FFFFFF", alpha=0.9))
        FG.append(P(n_stroke(9), stroke=p, sw=9, cap="round", join="round"))
        FG.append(P(n_stroke(9), stroke=g, sw=9, cap="round", join="round", salpha=0.35))
        FG.append(P(sine(87, 2, 36, 72, 1.5), stroke=sc, sw=2, cap="round", salpha=0.9))
    elif i == "solara":       # Soleil : demi-soleil rayonnant derrière le N, horizon ondulé
        BG.append(G("M0,0 H108 V108 H0 Z", [(0, sf), (1, bg)], 54, 0, 54, 108))
        BG.append(G(circle(54, 60, 50), [(0, sc), (1, "#00000000")], 54, 60, 50, 0, radial=True, alpha=0.5))
        rays = " ".join(f"M{54 + 24 * math.cos(math.radians(a)):.1f},{60 + 24 * math.sin(math.radians(a)):.1f} L{54 + 46 * math.cos(math.radians(a)):.1f},{60 + 46 * math.sin(math.radians(a)):.1f}" for a in range(195, 350, 15))
        BG.append(P(rays, stroke=sc, sw=2.6, cap="round", salpha=0.55))
        FG.append(G(circle(54, 60, 26), [(0, g), (1, p)], 54, 34, 54, 86))
        FG.append(P(N_PATH, fill=bg))
        FG.append(P(sine(80, 1.6, 32, 76, 2), stroke=acc, sw=2, cap="round", salpha=0.9))
        FG.append(P(sine(85, 1.6, 38, 70, 1.5), stroke=acc, sw=1.6, cap="round", salpha=0.6))
    elif i == "chaos_born":   # Avant-garde : N brisé en deux morceaux décalés, éclats, onde chaotique
        BG.append(G("M0,0 H108 V108 H0 Z", [(0, bg), (1, sf)], 0, 0, 108, 108))
        BG.append(P("M0,0 L40,0 L18,52 Z M108,108 L70,108 L96,60 Z M0,108 L0,70 L30,96 Z", fill=p, alpha=0.18))
        BG.append(P("M108,0 L108,30 L80,10 Z", fill=sc, alpha=0.22))
        FG.append(CLIP("M0,0 H108 L0,108 Z", ["<group android:translateX=\"-2\" android:translateY=\"-2\">", ngrad(t), "</group>"]))
        FG.append(CLIP("M108,0 V108 H0 Z", ["<group android:translateX=\"3\" android:translateY=\"3\">", ngrad(t), "</group>"]))
        FG.append(P("M0,0 L108,108", stroke=acc, sw=1.2, salpha=0.7))
        FG.append(wave_bars(acc, xs=[34, 39, 44, 49, 54, 59, 64, 69, 74], hs=[5, 2, 8, 3, 10, 4, 7, 2, 6], w=2.2, y=82, alpha=0.9))
    elif i == "survivor":     # Fierté : N tissé des 6 bandes arc-en-ciel, chevron Progress, onde trans, cœur
        BG.append(G("M0,0 H108 V108 H0 Z", [(0, sf), (1, bg)], 54, 0, 54, 108))
        rainbow = ["#E40303", "#FF8C00", "#FFED00", "#008026", "#004DFF", "#750787"]
        n_big = poly([(31, 76), (31, 28), (41, 28), (67, 59), (67, 28), (77, 28), (77, 76), (67, 76), (41, 45), (41, 76)])
        stripes = [P(f"M26,{28 + k * 8} H82 V{36 + k * 8} H26 Z", fill=c) for k, c in enumerate(rainbow)]
        # Chevron Progress (noir, brun, bleu, rose, blanc) à gauche
        chev = [("#000000", 0), ("#613915", 4), ("#5BCEFA", 8), ("#F5A9B8", 12), ("#FFFFFF", 16)]
        for c, o in reversed(chev):
            FG.append(P(f"M{o - 2},28 L{18 + o},54 L{o - 2},80 L{o - 6},80 L{14 + o},54 L{o - 6},28 Z", fill=c, alpha=0.95))
        FG.append(CLIP(n_big, stripes))
        # Onde = drapeau trans en 5 barres
        for x, c, h in zip([44, 49, 54, 59, 64], ["#5BCEFA", "#F5A9B8", "#FFFFFF", "#F5A9B8", "#5BCEFA"], [4, 6, 8, 6, 4]):
            FG.append(P(f"M{x},{83 - h / 2:g} V{83 + h / 2:g}", stroke=c, sw=2.8, cap="round"))
    elif i == "rainbow_pop":  # Chromatica : arcs concentriques colorés, N chromé, points
        BG.append(G("M0,0 H108 V108 H0 Z", [(0, bg), (1, sf)], 54, 0, 54, 108))
        for r, c in zip([44, 38, 32], [p, sc, acc]):
            BG.append(P(f"M{54 - r},70 A{r},{r} 0 0,1 {54 + r},70", stroke=c, sw=4.5, salpha=0.85))
        FG.append(G(N_PATH, [(0, "#FFFFFF"), (0.5, "#D8D8E6"), (1, "#8C8CA0")], 31, 32, 77, 76))
        FG.append(P(N_PATH, stroke=p, sw=1.2, salpha=0.8))
        FG.append(P("".join(circle(x, 82, 2.2) for x in [38, 46, 54, 62, 70]), fill=sc))
    elif i == "pop_revolution":  # K-pop : N italique, lignes de vitesse, fond scindé en diagonale
        BG.append(G("M0,0 H108 V108 H0 Z", [(0, bg), (1, sf)], 0, 0, 108, 108))
        BG.append(G("M0,0 H108 L0,108 Z", [(0, p), (1, sc)], 0, 0, 108, 108, alpha=0.35))
        FG.append(ngrad(t, n_skew(9)))
        FG.append(P("M20,40 H30 M18,48 H28 M22,56 H30", stroke=acc, sw=2.2, cap="round", salpha=0.9))
        FG.append(P("M84,60 H96 M86,68 H94 M82,76 H92", stroke=acc, sw=2.2, cap="round", salpha=0.9))
        FG.append(wave_bars(acc, xs=[40, 47, 54, 61, 68], hs=[3, 6, 10, 6, 3], w=2.6, y=82, alpha=0.9))
    elif i == "african_confessions":  # Reine de Saba : motifs géométriques tissés, soleil, N doré, zigzag
        BG.append(G("M0,0 H108 V108 H0 Z", [(0, bg), (1, sf)], 54, 0, 54, 108))
        BG.append(P(" ".join(f"M{x},0 L{x + 8},10 L{x},20 L{x - 8},10 Z" for x in range(0, 116, 16)) + " " + " ".join(f"M{x},88 L{x + 8},98 L{x},108 L{x - 8},98 Z" for x in range(8, 116, 16)), fill=sc, alpha=0.35))
        BG.append(P("M0,22 H108 M0,86 H108", stroke=acc, sw=1, salpha=0.5))
        BG.append(G(circle(54, 52, 30), [(0, sc), (1, "#00000000")], 54, 52, 30, 0, radial=True, alpha=0.45))
        FG.append(G(N_PATH, [(0, "#FFE9A6"), (0.5, sc), (1, p)], 31, 32, 77, 76))
        FG.append(P("M28,82 L34,78 L40,82 L46,78 L52,82 L58,78 L64,82 L70,78 L76,82 L80,80", stroke=acc, sw=1.8, join="round", cap="round", salpha=0.9))
    elif i == "bad_angel":    # Dualité : moitié lumière / moitié ombre, auréole, onde en arc
        BG.append(G("M0,0 H54 V108 H0 Z", [(0, "#F6F2FA"), (1, sf)], 0, 0, 54, 108))
        BG.append(G("M54,0 H108 V108 H54 Z", [(0, sf), (1, bg)], 54, 0, 108, 108))
        FG.append(P("M34,25 a20,5 0 1,0 40,0 a20,5 0 1,0 -40,0 Z", stroke=sc, sw=2, salpha=0.95))
        FG.append(CLIP("M0,0 H54 V108 H0 Z", [P(N_PATH, fill="#12101A")]))
        FG.append(CLIP("M54,0 H108 V108 H54 Z", [G(N_PATH, [(0, "#FFFFFF"), (1, p)], 54, 32, 77, 76)]))
        FG.append(P("M34,80 Q54,90 74,80", stroke=acc, sw=2, cap="round", salpha=0.85))
        FG.append(P("M40,85 Q54,92 68,85", stroke=acc, sw=1.4, cap="round", salpha=0.55))
    else:
        BG.append(G("M0,0 H108 V108 H0 Z", [(0, bg), (1, sf)], 54, 0, 54, 108))
        FG.append(ngrad(t)); FG.append(wave_bars(acc))
    return BG, FG

def _vector(body, comment):
    return f'''{HEADER}{GEN}<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <!-- {comment} -->
{body}
</vector>
'''

def fg_xml(t):
    return _vector("\n".join(design(t)[1]), f"Premier plan unique — {t['name']}")

def bg_xml(t):
    return _vector("\n".join(design(t)[0]), f"Fond unique — {t['name']}")

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

# ---------------------------------------------------------------- main
def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

def main():
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

if __name__ == "__main__":
    import sys
    main()
