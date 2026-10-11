"""Makes the launcher icon and the logos from tools/icon-source.png (run from the repo root:
python tools/make_icon.py; needs Pillow, numpy and scipy).

The source is a flat violet rounded square on white. The square's inside becomes the adaptive icon's
whole 108 dp layer, scaled so the 72 dp a launcher shows (under any mask: circle, squircle, square)
falls inside the violet: its straight edges and rounded corners stay out of sight, so no white ever
shows. Beyond the square the violet is continued smoothly (normalised blurs of its own pixels), only
as a safety margin. The monochrome layer (themed icons) is the light shapes on the violet: the page
with its lines and dots cut out, the clouds, sparkles and birds.

Writes res/mipmap-*/ic_launcher_art.webp and ic_launcher_mono.webp, site/logo-120.png (Google's
sign-in branding: the rounded square, transparent outside it), tools/logo-512.png (store-style,
full square) and a preview sheet in tools/emulator/shots/.
"""
import os
from PIL import Image, ImageFilter
import numpy as np
from scipy.ndimage import gaussian_filter, label, binary_erosion

OUT = 'tools/emulator/shots/icon-'
os.makedirs('tools/emulator/shots', exist_ok=True)
src = Image.open('tools/icon-source.png').convert('RGB')
a = np.asarray(src).astype(float)
H, W, _ = a.shape

# The rounded square: everything not connected to the white margin. (The page is nearly white too,
# but it sits inside the violet, so it isn't connected to the outside.)
whiteish = a.min(2) >= 247
lab, _ = label(whiteish)
outside = np.isin(lab, np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]])))
outside &= whiteish
square = ~outside
square = binary_erosion(square, iterations=5)  # off the anti-aliased rim
ys, xs = np.where(square)
x0s, x1s, y0s, y1s = xs.min(), xs.max(), ys.min(), ys.max()
cx, cy = (x0s + x1s) / 2, (y0s + y1s) / 2
inner = min(x1s - x0s, y1s - y0s)
mask = Image.fromarray((square * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.5))

# The launcher shows 72 of 108 dp; make that 88% of the square, so the square's sides (and its
# rounded corners, which a squircle or circle mask cuts off anyway) are always out of view.
visible = inner * 0.88
side = int(round(visible * 108 / 72))
ox, oy = int(round(cx - side / 2)), int(round(cy - side / 2))

# Continue the violet past the square: normalised blurs, wide to narrow.
base = Image.new('RGB', (side, side))
base.paste(src, (-ox, -oy))
wmask = Image.new('L', (side, side), 0)
wmask.paste(mask, (-ox, -oy))
wb = np.asarray(base).astype(float)
ww = np.asarray(wmask).astype(float) / 255
grad = None
for radius in (240, 120, 60, 30):
    num = np.dstack([gaussian_filter(wb[..., k] * ww, radius, mode='nearest') for k in range(3)])
    den = gaussian_filter(ww, radius, mode='nearest')[..., None]
    est = num / np.maximum(den, 1e-6)
    conf = np.clip(den * 4, 0, 1)
    grad = est if grad is None else grad * (1 - conf) + est * conf
canvas = Image.fromarray(np.clip(grad, 0, 255).astype(np.uint8))
canvas.paste(src, (-ox, -oy), mask)
full = canvas.resize((432, 432), Image.LANCZOS)
full.save(OUT + 'full432.png')

# Monochrome: the light shapes (page, clouds, sparkles, birds) by brightness: the violet is about
# 150-175, the light shapes 200 and up.
crop = np.asarray(canvas).astype(float)
lum = crop @ np.array([0.3, 0.59, 0.11])
fg = np.clip((lum - 186) / 12, 0, 1)
# The page (near white) with its lines and dots cut out: close the page over its lines to find its
# outline, then anything less than white inside it is a hole.
from scipy.ndimage import binary_closing, binary_fill_holes
page = binary_fill_holes(binary_closing(lum > 236, iterations=30))
holes = page & (lum < 232)
alpha = np.where(page, 1 - holes, fg)
alpha = gaussian_filter(alpha.astype(float), 1.2)
mono_big = Image.fromarray((np.clip(alpha, 0, 1) * 255).astype(np.uint8)).resize((432, 432), Image.LANCZOS)
mono = Image.merge('RGBA', [Image.new('L', (432, 432), 255)] * 3 + [mono_big])
mono.save(OUT + 'mono432.png')

# Previews: what launchers show (central 72 of 108 dp) under a squircle and a circle, and themed.
vis = full.crop((72, 72, 360, 360))
from PIL import ImageDraw
def masked(shape):
    mk = Image.new('L', (288 * 4, 288 * 4), 0)
    d = ImageDraw.Draw(mk)
    if shape == 'circle':
        d.ellipse((0, 0, 288 * 4 - 1, 288 * 4 - 1), fill=255)
    else:
        d.rounded_rectangle((0, 0, 288 * 4 - 1, 288 * 4 - 1), radius=int(288 * 4 * 0.30), fill=255)
    mk = mk.resize((288, 288), Image.LANCZOS)
    o = Image.new('RGB', (288, 288), (240, 240, 245))
    o.paste(vis, (0, 0), mk)
    return o
sheet = Image.new('RGB', (288 * 3 + 40, 288 + 20), (240, 240, 245))
sheet.paste(masked('squircle'), (10, 10))
sheet.paste(masked('circle'), (308, 10))
mp = Image.new('RGB', (288, 288), (60, 60, 80))
mp.paste(Image.new('RGB', (288, 288), (220, 215, 255)), (0, 0), mono.crop((72, 72, 360, 360)).split()[3])
sheet.paste(mp, (616, 10))
sheet.save(OUT + 'preview.png')

for name, px in (('mdpi', 108), ('hdpi', 162), ('xhdpi', 216), ('xxhdpi', 324), ('xxxhdpi', 432)):
    folder = f'app/src/main/res/mipmap-{name}/'
    os.makedirs(folder, exist_ok=True)
    canvas.resize((px, px), Image.LANCZOS).save(folder + 'ic_launcher_art.webp', quality=92, method=6)
    mono.resize((px, px), Image.LANCZOS).save(folder + 'ic_launcher_mono.webp', lossless=True, method=6)

# Logos: the rounded square itself, trimmed, transparent outside it.
rgba = src.convert('RGBA')
rgba.putalpha(Image.fromarray((~outside * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.8)))
pad = 4
box = (x0s - 5 - pad, y0s - 5 - pad, x1s + 5 + pad, y1s + 5 + pad)
s = max(box[2] - box[0], box[3] - box[1])
cxb, cyb = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
logo = rgba.crop((int(cxb - s / 2), int(cyb - s / 2), int(cxb + s / 2), int(cyb + s / 2)))
logo.resize((120, 120), Image.LANCZOS).save('site/logo-120.png')
# A full square (no white, no transparency), as stores and Google's branding page like it.
square_logo = canvas.crop((int(side / 2 - inner * 0.5), int(side / 2 - inner * 0.5), int(side / 2 + inner * 0.5), int(side / 2 + inner * 0.5)))
square_logo.resize((512, 512), Image.LANCZOS).save('tools/logo-512.png')
square_logo.resize((120, 120), Image.LANCZOS).save('site/logo-120-square.png')
print('square', x0s, x1s, y0s, y1s, 'layer side', side)
