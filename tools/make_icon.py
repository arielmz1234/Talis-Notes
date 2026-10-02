"""Makes the launcher icon from tools/icon-source.png (run from the repo root: python tools/make_icon.py; needs Pillow, numpy and scipy).

Crops the violet rounded square's page and cloud so they fill about 56 of the 72 dp a launcher shows,
extends the violet with a fitted gradient out to the full 108 dp layer, and writes the colour art and a
monochrome silhouette (for themed icons) into res/mipmap-*. A preview sheet goes to tools/emulator/shots/.
"""
import os
from PIL import Image, ImageFilter, ImageDraw
import numpy as np
from scipy.ndimage import gaussian_filter
OUT='tools/emulator/shots/icon-'
os.makedirs('tools/emulator/shots', exist_ok=True)
src=Image.open('tools/icon-source.png').convert('RGB')
a=np.asarray(src).astype(float)
H,W,_=a.shape
bg=a[20,20]
# The violet rounded square, measured from the picture: edges 146/119/1108/1121, corners about r=260.
# Drawn at 4x and inset a few pixels so none of the drop shadow survives, then feathered.
mk=Image.new('L',(W*4,H*4),0)
ImageDraw.Draw(mk).rounded_rectangle(((146+14)*4,(119+14)*4,(1108-14)*4,(1121-14)*4),radius=250*4,fill=255)
m=mk.resize((W,H),Image.LANCZOS).filter(ImageFilter.GaussianBlur(9))
# Canvas in source pixels, centred on the page+cloud, so they fill ~56 of the 72 visible dp.
cx,cy=567,627; content=765; scale_dp=56/content
side=int(round(108/scale_dp)); x0=cx-side//2; y0=cy-side//2
# Continue the violet outwards smoothly: normalised blurs of the square's own pixels (page and clouds left out),
# from wide to narrow, so far areas get the overall tone and areas near the edge follow the edge's colour.
base=Image.new('RGB',(side,side)); base.paste(src,(-x0,-y0))
inner=np.asarray(m).astype(float)/255*(a.min(2)<215)
wmask=Image.new('L',(side,side),0); wmask.paste(Image.fromarray((inner*255).astype(np.uint8)),(-x0,-y0))
wb=np.asarray(base).astype(float); ww=np.asarray(wmask).astype(float)/255
grad=None
for radius in (240,120,60,30):
  num=np.dstack([gaussian_filter(wb[...,k]*ww,radius,mode='nearest') for k in range(3)])
  den=gaussian_filter(ww,radius,mode='nearest')[...,None]
  est=num/np.maximum(den,1e-6)
  conf=np.clip(den*4,0,1)
  grad=est if grad is None else grad*(1-conf)+est*conf
grad=np.clip(grad,0,255)
canvas=Image.fromarray(grad.astype(np.uint8))
canvas.paste(src,(-x0,-y0),m)
# Ensure areas beyond the source image use the gradient only (paste already handles that since src doesn't cover them).
full=canvas.resize((432,432),Image.LANCZOS)
full.save(OUT+'full432.png')
# Monochrome: the white page and cloud as a silhouette (the lilac lines become gaps).
s=np.asarray(canvas.convert('RGB')).astype(float)
lum=np.asarray(Image.fromarray(s.min(2).astype(np.uint8)).filter(ImageFilter.GaussianBlur(2.5))).astype(float)
alpha=np.clip((lum-170)/14,0,1)*255
# Keep only the page and the front cloud: drop the background clouds at the sides.
ax=np.arange(x0,x0+side)[None,:]; ay=np.arange(y0,y0+side)[:,None]
alpha=alpha*~(((ax<355)&(ay<645))|(ax>965)|(ay>1000))
mono=Image.fromarray(np.dstack([np.full_like(alpha,255)]*3+[alpha]).astype(np.uint8),'RGBA').resize((432,432),Image.LANCZOS)
mono.save(OUT+'mono432.png')
# Previews: what launchers show (central 72 of 108 dp) under a squircle and a circle mask.
vis=full.crop((72,72,360,360))
def masked(shape):
  mk=Image.new('L',(288*4,288*4),0); d=ImageDraw.Draw(mk)
  if shape=='circle': d.ellipse((0,0,288*4-1,288*4-1),fill=255)
  else: d.rounded_rectangle((0,0,288*4-1,288*4-1),radius=int(288*4*0.30),fill=255)
  mk=mk.resize((288,288),Image.LANCZOS)
  o=Image.new('RGB',(288,288),(240,240,245)); o.paste(vis,(0,0),mk); return o
sheet=Image.new('RGB',(288*3+40,288+20),(240,240,245))
sheet.paste(masked('squircle'),(10,10)); sheet.paste(masked('circle'),(298+10,10))
mp=Image.new('RGB',(288,288),(60,60,80)); mp.paste(Image.new('RGB',(288,288),(220,215,255)),(0,0),mono.crop((72,72,360,360)).split()[3]); sheet.paste(mp,(596+20,10))
sheet.save(OUT+'preview.png')
for name, px in (('mdpi', 108), ('hdpi', 162), ('xhdpi', 216), ('xxhdpi', 324), ('xxxhdpi', 432)):
  folder = f'app/src/main/res/mipmap-{name}/'
  os.makedirs(folder, exist_ok=True)
  full.resize((px, px), Image.LANCZOS).save(folder + 'ic_launcher_art.webp', quality=92, method=6)
  mono.resize((px, px), Image.LANCZOS).save(folder + 'ic_launcher_mono.webp', lossless=True, method=6)
