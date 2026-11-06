"""Detect phrase starts from pauses without editing existing audio."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import subprocess,re,json
ROOT=Path(__file__).resolve().parents[1]
files=sorted((ROOT/'android/app/src/main/assets/audio').glob('*.mp3'))
def detect(p):
    duration=float(subprocess.run(['ffprobe','-v','error','-show_entries','format=duration','-of','csv=p=0',str(p)],capture_output=True,text=True,check=True).stdout)
    r=subprocess.run(['ffmpeg','-v','info','-i',str(p),'-af','silencedetect=noise=-38dB:d=0.2','-f','null','-'],capture_output=True,text=True,check=True)
    starts=[0]
    for v in re.findall(r'silence_end: ([0-9.]+)',r.stderr):
        marker=max(0,int(float(v)*1000)-50)
        if marker-starts[-1]>=600 and marker<int(duration*1000)-300:starts.append(marker)
    return 'audio/'+p.name,dict(duration_ms=round(duration*1000),starts_ms=starts)
with ThreadPoolExecutor(max_workers=6) as pool:result=dict(pool.map(detect,files))
assert len(result)>=515
for value in result.values():assert value['starts_ms'][0]==0 and all(0<=t<value['duration_ms'] for t in value['starts_ms'])
for dest in [ROOT/'data/audio_cues.json',ROOT/'android/app/src/main/assets/audio_cues.json']:
    dest.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print('Prepared pause-based cues for',len(result),'unchanged MP3 files')
