"""Tiny emulator driver.

Usage: python drive.py CMD [CMD ...]
  tap X Y                 finger tap
  stap X Y                stylus tap
  stroke X,Y X,Y ...      stylus stroke through the points (interpolated every ~12 px)
  hold X,Y X,Y ...        stylus stroke, then hold still at the end (for hold-to-shape)
  drag X,Y X,Y            finger drag
  key NAME                keyevent
  text STRING             type text
  wait SECONDS
  shot NAME               screenshot to tools/emulator/shots/NAME.png
  log                     print new crash/error lines from logcat
"""
import math
import os
import subprocess
import sys
import time

ADB = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")
HERE = os.path.dirname(os.path.abspath(__file__))


def sh(cmd):
    return subprocess.run([ADB, "shell", cmd], capture_output=True, text=True).stdout


def pts(args):
    return [tuple(float(v) for v in a.split(",")) for a in args]


def dense(points, step=12.0):
    out = [points[0]]
    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        n = max(1, int(math.hypot(x1 - x0, y1 - y0) / step))
        for i in range(1, n + 1):
            out.append((x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n))
    return out


def stroke(points, hold=False, source="stylus"):
    p = dense(points)
    cmds = [f"input {source} motionevent DOWN {p[0][0]:.0f} {p[0][1]:.0f}"]
    cmds += [f"input {source} motionevent MOVE {x:.0f} {y:.0f}" for x, y in p[1:]]
    if hold:
        x, y = p[-1]
        cmds += ["sleep 1.2", f"input {source} motionevent MOVE {x:.0f} {y:.0f}"]
    cmds.append(f"input {source} motionevent UP {p[-1][0]:.0f} {p[-1][1]:.0f}")
    sh("; ".join(cmds))


def main(argv):
    i = 0
    while i < len(argv):
        c = argv[i]
        i += 1
        if c == "tap":
            sh(f"input touchscreen tap {argv[i]} {argv[i+1]}"); i += 2
        elif c == "stap":
            sh(f"input stylus tap {argv[i]} {argv[i+1]}"); i += 2
        elif c in ("stroke", "hold", "drag"):
            j = i
            while j < len(argv) and "," in argv[j]:
                j += 1
            points = pts(argv[i:j]); i = j
            if c == "drag":
                stroke(points, source="touchscreen")
            else:
                stroke(points, hold=(c == "hold"))
        elif c == "key":
            sh(f"input keyevent {argv[i]}"); i += 1
        elif c == "text":
            sh(f"input text '{argv[i]}'"); i += 1
        elif c == "wait":
            time.sleep(float(argv[i])); i += 1
        elif c == "shot":
            os.makedirs(os.path.join(HERE, "shots"), exist_ok=True)
            path = os.path.join(HERE, "shots", argv[i] + ".png"); i += 1
            with open(path, "wb") as f:
                f.write(subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True).stdout)
            print(path)
        elif c == "log":
            out = subprocess.run([ADB, "logcat", "-d", "-v", "brief", "AndroidRuntime:E", "TaliNotes:*", "*:S"], capture_output=True, text=True).stdout
            print(out.strip() or "(no errors)")
            subprocess.run([ADB, "logcat", "-c"])
        else:
            raise SystemExit("unknown command " + c)


main(sys.argv[1:])
