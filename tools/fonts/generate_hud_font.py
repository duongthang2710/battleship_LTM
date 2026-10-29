"""Generate an OFL Roboto Condensed Bold bitmap HUD font, including Vietnamese glyphs."""
from pathlib import Path
from PIL import Image, ImageFont, ImageDraw
import shutil, urllib.request
SOURCE = Path("build/hud-reference")
OUT = Path("frontend/src/main/resources/fonts")
SOURCE.mkdir(parents=True, exist_ok=True)
for remote, local in (("RobotoCondensed%5Bwght%5D.ttf", "RobotoCondensed.ttf"), ("OFL.txt", "RobotoCondensed-OFL.txt")):
    if not (SOURCE/local).exists():
        urllib.request.urlretrieve("https://raw.githubusercontent.com/google/fonts/main/ofl/robotocondensed/"+remote, SOURCE/local)
font=ImageFont.truetype(str(SOURCE/"RobotoCondensed.ttf"),64)
font.set_variation_by_axes([700])
ascent,descent=font.getmetrics()
codes=sorted(set(range(32,127))|set(range(160,256))|set(range(0x1EA0,0x1EFA))|{0x102,0x103,0x110,0x111,0x1A0,0x1A1,0x1AF,0x1B0,0x2013,0x2014,0x2026})
atlas=Image.new("RGBA",(2048,2048),(255,255,255,0));draw=ImageDraw.Draw(atlas)
x=y=4;row_height=0;glyphs=[]
for code in codes:
    ch=chr(code);l,t,r,b=font.getbbox(ch,anchor="ls");w,h=max(0,r-l),max(0,b-t)
    if x+w+4>2048:x=4;y+=row_height+4;row_height=0
    if y+h+4>2048:raise RuntimeError("Atlas overflow")
    draw.text((x-l,y-t),ch,font=font,fill="white",anchor="ls")
    glyphs.append(f"char id={code} x={x} y={y} width={w} height={h} xoffset={l} yoffset={ascent+t} xadvance={round(font.getlength(ch))} page=0 chnl=15")
    x+=w+4;row_height=max(row_height,h)
atlas.save(OUT/"NavalHud.png")
header=[f'info face="Roboto Condensed Bold" size=64 bold=1 italic=0 charset="" unicode=1 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=1,1',f'common lineHeight={ascent+descent} base={ascent} scaleW=2048 scaleH=2048 pages=1 packed=0','page id=0 file="NavalHud.png"',f'chars count={len(glyphs)}']
(OUT/"NavalHud.fnt").write_text("\n".join(header+glyphs)+"\n",encoding="utf-8")
shutil.copyfile(SOURCE/"RobotoCondensed-OFL.txt",OUT/"NavalHud-OFL.txt")
print("Generated Roboto Condensed Bold HUD font:",len(glyphs),"glyphs")
