// Muse Cyrene original geometric artwork generator, 2026-10-18. GPL-3.0-or-later.
// No input images or third-party graphics. Node.js standard library only.
const fs=require('fs'),path=require('path'),zlib=require('zlib');
const root=path.resolve(__dirname,'../..');
const assets=[
  {
    "path": "app/src/main/res/drawable/ic_muse_logo.png",
    "width": 1536,
    "height": 1024,
    "kind": "icon"
  },
  {
    "path": "app/src/main/res/drawable-nodpi/default_assistant_avatar.png",
    "width": 1254,
    "height": 1254,
    "kind": "avatar"
  },
  {
    "path": "app/src/main/res/mipmap-hdpi/ic_launcher.png",
    "width": 72,
    "height": 72,
    "kind": "icon"
  },
  {
    "path": "app/src/main/res/mipmap-hdpi/ic_launcher_foreground.png",
    "width": 162,
    "height": 162,
    "kind": "icon"
  },
  {
    "path": "app/src/main/res/mipmap-hdpi/ic_launcher_round.png",
    "width": 72,
    "height": 72,
    "kind": "icon"
  },
  {
    "path": "app/src/main/res/mipmap-mdpi/ic_launcher.png",
    "width": 48,
    "height": 48,
    "kind": "icon"
  },
  {
    "path": "app/src/main/res/mipmap-mdpi/ic_launcher_foreground.png",
    "width": 108,
    "height": 108,
    "kind": "icon"
  },
  {
    "path": "app/src/main/res/mipmap-mdpi/ic_launcher_round.png",
    "width": 48,
    "height": 48,
    "kind": "icon"
  },
  {
    "path": "app/src/main/res/mipmap-xhdpi/ic_launcher.png",
    "width": 96,
    "height": 96,
    "kind": "icon"
  },
  {
    "path": "app/src/main/res/mipmap-xhdpi/ic_launcher_foreground.png",
    "width": 216,
    "height": 216,
    "kind": "icon"
  },
  {
    "path": "app/src/main/res/mipmap-xhdpi/ic_launcher_round.png",
    "width": 96,
    "height": 96,
    "kind": "icon"
  },
  {
    "path": "app/src/main/res/mipmap-xxhdpi/ic_launcher.png",
    "width": 144,
    "height": 144,
    "kind": "icon"
  },
  {
    "path": "app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.png",
    "width": 324,
    "height": 324,
    "kind": "icon"
  },
  {
    "path": "app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png",
    "width": 144,
    "height": 144,
    "kind": "icon"
  },
  {
    "path": "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
    "width": 192,
    "height": 192,
    "kind": "icon"
  },
  {
    "path": "app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png",
    "width": 432,
    "height": 432,
    "kind": "icon"
  },
  {
    "path": "app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png",
    "width": 192,
    "height": 192,
    "kind": "icon"
  }
];
function crc(b){let n=0xffffffff;for(const x of b){n^=x;for(let j=0;j<8;j++)n=(n>>>1)^((n&1)?0xedb88320:0);}return (n^0xffffffff)>>>0;}
function chunk(type,data){const tag=Buffer.from(type),out=Buffer.alloc(data.length+12);out.writeUInt32BE(data.length);tag.copy(out,4);data.copy(out,8);out.writeUInt32BE(crc(Buffer.concat([tag,data])),data.length+8);return out;}
function circle(x,y,cx,cy,r){return (x-cx)**2+(y-cy)**2<=r*r;}
function pixel(x,y,kind){let bg=[0,0,0,0];if(kind==='avatar'){if(circle(x,y,.5,.5,.47))bg=[41,35,72,255];if(circle(x,y,.5,.38,.14)||((y>.57&&y<.82)&&circle(x,y,.5,.83,.27)))bg=[190,215,230,255];return bg;}if(x>=.15&&x<=.85&&y>=.22&&y<=.72)bg=[84,65,133,255];if(y>.72&&y<.84&&x>=.24&&x<=.40-(y-.72))bg=[84,65,133,255];for(let k=0;k<3;k++){let h=[.13,.23,.17][k],cx=.35+k*.15;if(Math.abs(x-cx)<.035&&Math.abs(y-.47)<h/2)bg=[188,239,231,255];}return bg;}
for(const a of assets){let raw=Buffer.alloc((a.width*4+1)*a.height),scale=Math.min(a.width,a.height),ox=(a.width-scale)/2,oy=(a.height-scale)/2;for(let y=0;y<a.height;y++){let off=y*(a.width*4+1)+1;for(let x=0;x<a.width;x++){const rgba=pixel((x+.5-ox)/scale,(y+.5-oy)/scale,a.kind);for(let k=0;k<4;k++)raw[off+x*4+k]=rgba[k];}}let hdr=Buffer.alloc(13);hdr.writeUInt32BE(a.width);hdr.writeUInt32BE(a.height,4);hdr[8]=8;hdr[9]=6;let png=Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',hdr),chunk('IDAT',zlib.deflateSync(raw,{level:9})),chunk('IEND',Buffer.alloc(0))]);fs.writeFileSync(path.join(root,a.path),png);}
console.log('Generated '+assets.length+' geometric assets.');
