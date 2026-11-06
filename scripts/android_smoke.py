"""Small adb UI helper for the project emulator. No runtime dependency in the app."""
import subprocess,xml.etree.ElementTree as ET,re,time,os
from pathlib import Path
SERIAL=os.environ.get('ANDROID_SERIAL','emulator-5554')
def adb(*args,**kw):
    return subprocess.run(['adb','-s',SERIAL,*args],check=True,capture_output=True,**kw).stdout

def nodes():
    for attempt in range(3):
        try:
            adb('shell','uiautomator','dump','/sdcard/interview-window.xml')
            return ET.fromstring(adb('shell','cat','/sdcard/interview-window.xml'))
        except (subprocess.CalledProcessError,ET.ParseError):
            if attempt==2:raise
            time.sleep(1)
def tap(text=None,desc=None,contains=False):
    for n in nodes().iter('node'):
        value=n.get('text','') if text is not None else n.get('content-desc','')
        needle=text if text is not None else desc
        if (needle in value if contains else value==needle):
            x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
            adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.4);return value
    raise AssertionError(f'Not found: {text or desc}')
def texts(): return [n.get('text') for n in nodes().iter('node') if n.get('text')]
def shot(name):
    path=Path(__file__).resolve().parents[1]/'docs/screenshots'/f'{name}.png'
    path.write_bytes(adb('exec-out','screencap','-p'))
if __name__=='__main__':
    print('\n'.join(texts()))
