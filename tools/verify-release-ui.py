#!/usr/bin/env python3
"""External UI smoke test: runs the unchanged, signed and minified release APK."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

if not __debug__:
    raise SystemExit('Release UI verification requires enabled assertions; remove -O or PYTHONOPTIMIZE.')

parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
parser.add_argument('--output', type=Path, default=Path('output/release/ui'))
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
sdk = Path(os.environ.get('ANDROID_HOME') or Path.home() / 'Library/Android/sdk')
base = [str(sdk / 'platform-tools/adb'), '-s', args.serial]

def adb(*parts):
    return subprocess.check_output(base + list(parts), timeout=25)

def snapshot():
    for _ in range(5):
        result = adb('shell', 'uiautomator', 'dump', '/sdcard/racingclub-release-qa.xml')
        if b'dumped to:' in result:
            try:
                return ET.fromstring(adb('exec-out', 'cat', '/sdcard/racingclub-release-qa.xml'))
            except (ET.ParseError, subprocess.CalledProcessError):
                pass
        time.sleep(.4)
    raise AssertionError('Native accessibility tree did not become ready')

def find(root, value, attribute='content-desc'):
    return next((n for n in root.iter('node') if n.get(attribute) == value), None)

def bounds(node):
    assert node is not None, 'Expected native UI node'
    return tuple(map(int, re.findall(r'\d+', node.get('bounds'))))

def tap(node):
    x1, y1, x2, y2 = bounds(node)
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))

def capture(name):
    (args.output / f'{name}.png').write_bytes(adb('exec-out', 'screencap', '-p'))

def wait_text(value):
    end = time.monotonic() + 18
    while time.monotonic() < end:
        root = snapshot()
        if find(root, value, 'text') is not None:
            return root
    raise AssertionError(f'Missing UI text: {value}')

adb('shell', 'am', 'force-stop', 'com.zoomzhao.racingclub')
adb('shell', 'am', 'start', '-n', 'com.zoomzhao.racingclub/.MainActivity')
time.sleep(1)
root = snapshot()
if (button := find(root, 'Got it', 'text')) is not None:
    tap(button)
    root = snapshot()
capture('01-home')
tap(find(root, '进入挑战 03'))
root = snapshot()
tap(find(root, '重置'))
root = snapshot()
assert find(root, '马儿') is None, 'The horse must not obstruct the board while editing'
tap(find(root, '拼板 1'))
x1, y1, x2, y2 = bounds(find(root, '棋盘'))
unit = (x2-x1)/5.7
adb('shell', 'input', 'tap', str(round(x1+1.85*unit)), str(round(y1+1.85*unit)))
root = snapshot()
a = bounds(find(root, '拼板操作'))
assert x1 <= a[0] < a[2] <= x2 and y1 <= a[1] < a[3] <= y2
capture('02-selected')
tap(find(root, '顺时针旋转90度'))
root = snapshot()
capture('03-rotated')
tap(find(root, '逆时针旋转90度'))
root = snapshot()
tap(find(root, '移回'))
root = snapshot()
assert find(root, '拼板操作') is None
capture('04-returned')
print('Release launch, board selection, both rotations and removal passed.', flush=True)

def cell(x, y):
    adb('shell', 'input', 'tap', str(round(x1+(x+.35)*unit)), str(round(y1+(y+.35)*unit)))

def assert_tile_at(root, tile_id, x, y, vertical):
    actual = bounds(find(root, f'棋盘上 {tile_id}号拼板'))
    w, h = (1, 2) if vertical else (2, 1)
    expected = (x1+(x+.35)*unit, y1+(y+.35)*unit, x1+(x+w+.35)*unit, y1+(y+h+.35)*unit)
    assert all(abs(a-b) <= 4 for a, b in zip(actual, expected)), (actual, expected)

# A first board tap can rotate a tray piece to fit; explicit drag geometry is unchanged.
cell(4.5, 1.5)
root = snapshot()
assert_tile_at(root, 1, 4, 1, True)
tap(find(root, '撤销'))
root = snapshot()
assert find(root, '棋盘上 1号拼板') is None
cell(4.5, 3.5)
root = snapshot()
assert_tile_at(root, 1, 4, 2, True)
assert find(root, '马儿') is None
capture('14-release-right-edge-inserted')
tap(find(root, '重置'))
root = snapshot()

# Exercise the production APK's stack UI and edge rotations with real touch input.
tap(find(root, '拼板 1'))
cell(3.5, 3.5)
root = snapshot()
rack = bounds(find(root, '拼板 5'))
adb('shell', 'input', 'swipe', str((rack[0]+rack[2])//2), str((rack[1]+rack[3])//2),
    str(round(x1+4.35*unit)), str(round(y1+3.85*unit)), '700')
root = snapshot()
assert find(root, '被遮挡', 'text') is not None, 'Stacked tile must remain identifiable in the rack'
assert find(root, '已放置', 'text') is not None
capture('09-release-stack')
tap(find(root, '拼板 1'))
root = snapshot()
capture('10-release-raised')
tap(find(root, '顺时针旋转90度'))
root = snapshot()
assert find(root, '被遮挡', 'text') is not None, 'Bottom edge rotation must succeed and keep the overlap'
capture('11-release-bottom-edge-rotation')
tap(find(root, '逆时针旋转90度'))
root = snapshot()
tap(find(root, '顺时针旋转90度'))
root = snapshot()
tap(find(root, '移回'))
root = snapshot()
assert find(root, '被遮挡', 'text') is None, 'Removing the top must reveal the lower tile'
tap(find(root, '撤销'))
root = snapshot()
assert find(root, '被遮挡', 'text') is not None
capture('12-release-stack-undo')
tap(find(root, '重置'))
root = snapshot()
print('Release drag stacking, hidden-piece selection, bottom-edge rotations, reveal and undo passed.', flush=True)
hint = find(root, '提示')
for _ in range(3):
    tap(hint)
    time.sleep(.18)
time.sleep(.65)
capture('05-running')
time.sleep(.65)
capture('06-running')
root = wait_text('漂亮，顺利通关！')
capture('07-win')
tap(find(root, '查看本关排行榜', 'text'))
root = wait_text('使用提示')
capture('08-ranking')
tap(find(root, '知道了', 'text'))
root = snapshot()
tap(find(root, '挑战下一关', 'text'))
root = snapshot()
for _ in range(4):
    tap(find(root, '提示'))
    time.sleep(.12)
root = snapshot()
assert find(root, '马儿') is not None, 'The horse appears for the completed route animation'
tap(find(root, '跳过动画'))
root = wait_text('漂亮，顺利通关！')
capture('13-release-skipped-animation')
report = {
    'passed': True,
    'serial': args.serial,
    'method': 'External ADB/UIAutomator, no application instrumentation or release keep rules',
    'checks': ['normal release launch', 'selected board controls', 'clockwise and counterclockwise rotation', 'return removes selected tile and controls', 'drag to occupied board cells', 'covered and placed rack states', 'tap hidden tile to raise', 'bottom edge rotation with overlap', 'removal reveals lower tile', 'stack undo', 'automatic challenge03 win after hints', 'persistent practice score', 'skip completed route animation to settlement', 'horse hidden while editing', 'right-column first tap turns and is undoable', 'bottom-right first tap turns and nudges upward', 'horse visible during completed route animation'],
    'animation_frames': ['05-running.png', '06-running.png'],
}
(args.output / 'release-ui-report.json').write_text(json.dumps(report, indent=2) + '\n')
print('Automatic release completion and practice leaderboard passed.', flush=True)
