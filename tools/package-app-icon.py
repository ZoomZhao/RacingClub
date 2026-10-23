"""Package the approved ImageGen raster without changing its artwork."""
from pathlib import Path
import json
from PIL import Image

root = Path(__file__).resolve().parents[1]
source = root / 'design/app-icon/racingclub-icon-source-v1.png'
image = Image.open(source).convert('RGB')
assert image.width == image.height, 'Launcher master must be square'
res = root / 'native/android/app/src/main/res'
for density, size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    folder = res / f'mipmap-{density}'
    folder.mkdir(parents=True, exist_ok=True)
    image.resize((size,size), Image.Resampling.LANCZOS).save(folder/'ic_launcher.png', optimize=True)
folder = res/'drawable-nodpi'
image.resize((432,432), Image.Resampling.LANCZOS).save(folder/'ic_launcher_art.png', optimize=True)
folder = res/'drawable'
folder.mkdir(exist_ok=True)
(folder/'ic_launcher_foreground.xml').write_text('''<inset xmlns:android="http://schemas.android.com/apk/res/android" android:inset="16.666667%">
    <bitmap android:src="@drawable/ic_launcher_art" android:gravity="fill" android:filter="true"/>
</inset>
''')
folder = res/'mipmap-anydpi-v26'
folder.mkdir(exist_ok=True)
adaptive = '''<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
'''
for name in ['ic_launcher','ic_launcher_round']:
    (folder/f'{name}.xml').write_text(adaptive)
(res/'values/launcher.xml').write_text('''<resources>
    <color name="launcher_background">#043F24</color>
</resources>
''')
# iPhone/iPad entries plus the opaque App Store marketing master.
assets = root/'native/ios/RacingClub/Assets.xcassets'
assets.mkdir(exist_ok=True)
(assets/'Contents.json').write_text(json.dumps({'info':{'author':'xcode','version':1}},indent=2)+'\n')
folder = assets/'AppIcon.appiconset'
folder.mkdir(exist_ok=True)
entries=[]
for idiom, sizes in [('iphone',[(20,[2,3]),(29,[2,3]),(40,[2,3]),(60,[2,3])]),('ipad',[(20,[1,2]),(29,[1,2]),(40,[1,2]),(76,[1,2]),(83.5,[2])]),('ios-marketing',[(1024,[1])])]:
    for size, scales in sizes:
        for scale in scales:
            pixels=int(size*scale)
            filename=f'icon-{pixels}.png'
            image.resize((pixels,pixels),Image.Resampling.LANCZOS).save(folder/filename,optimize=True)
            entries.append({'idiom':idiom,'size':f'{size}x{size}','scale':f'{scale}x','filename':filename})
(folder/'Contents.json').write_text(json.dumps({'images':entries,'info':{'author':'xcode','version':1}},indent=2)+'\n')
print('Packaged Android adaptive/legacy icons and opaque iPhone/iPad/App Store icons.')
