"""Resumable Edge TTS generation with text hashes, atomic writes and MP3 decoding checks."""
import argparse
import asyncio
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import edge_tts

ROOT=Path(__file__).resolve().parents[1]
VOICE='en-US-AndrewMultilingualNeural'
RATE='-8%'
NORMALIZER_VERSION=1

def digest(value):
    return hashlib.sha256(value.encode()).hexdigest()

def speech(text, glossary):
    # Single pass prevents re-normalizing words that have already been replaced.
    keys=sorted(glossary,key=len,reverse=True)
    pattern='|'.join((r'(?<![\w])' if k[0].isalnum() or k[0]=='_' else '')+re.escape(k)+(r'(?![\w])' if k[-1].isalnum() or k[-1]=='_' else '') for k in keys)
    text=re.sub(pattern,lambda m: glossary[m[0]],text)
    text=text.replace('/', ', ').replace('_',' ').replace('`','').replace('•', ', ')
    return re.sub(r'\s+', ' ',text).strip()

def inspect_audio(path):
    probe=subprocess.run(['ffprobe','-v','error','-show_entries','format=duration','-of','json',str(path)],capture_output=True,text=True,check=True)
    duration=float(json.loads(probe.stdout)['format']['duration'])
    if duration<=0 or path.stat().st_size<500:
        raise ValueError('Empty audio')
    subprocess.run(['ffmpeg','-v','error','-xerror','-i',str(path),'-f','null','-'],capture_output=True,check=True)
    return duration

async def run(args):
    datafile=ROOT/'data/cards.json'
    deck=json.loads(datafile.read_text())
    glossary=json.loads((ROOT/'data/pronunciations.json').read_text())
    for card in deck['cards']:
        for role in ('question','answer'):
            card[role+'_speech']=speech(card[role],glossary)
    datafile.write_text(json.dumps(deck,ensure_ascii=False,indent=2)+'\n')
    out=ROOT/'audio';out.mkdir(exist_ok=True)
    manifest_path=out/'manifest.json'
    old=json.loads(manifest_path.read_text()) if manifest_path.exists() else {}
    records=old.get('files',{}) if old.get('voice')==VOICE and old.get('rate')==RATE else {}
    # Preserve known metadata across interrupted runs, but validate each cache entry.
    metadata=dict(schema_version=1, provider='Microsoft Edge online TTS via edge-tts', client_version=edge_tts.__version__, voice=VOICE, rate=RATE, pitch='+0Hz', normalizer_version=NORMALIZER_VERSION, source_sha256=deck['source_sha256'], generated_at=datetime.now(timezone.utc).isoformat(), files=records)
    def checkpoint():
        tmp=manifest_path.with_suffix('.json.part');tmp.write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n');tmp.replace(manifest_path)
    checkpoint()
    sem=asyncio.Semaphore(args.concurrency)
    failures=[]
    async def make(card,role):
        path=ROOT/card[role+'_audio'];text=card[role+'_speech']
        fingerprint=digest(json.dumps([text,VOICE,RATE,NORMALIZER_VERSION,edge_tts.__version__]))
        async with sem:
            rec=records.get(path.name,{})
            if path.exists() and rec.get('fingerprint')==fingerprint:
                try:
                    await asyncio.to_thread(inspect_audio,path)
                    if hashlib.sha256(path.read_bytes()).hexdigest()==rec.get('audio_sha256'):
                        print('CACHED',path.name,flush=True);return
                except Exception:
                    pass
            tmp=path.with_suffix('.part.mp3')
            for attempt in range(8):
                try:
                    await asyncio.wait_for(edge_tts.Communicate(text,VOICE,rate=RATE).save(str(tmp)),timeout=120)
                    duration=await asyncio.to_thread(inspect_audio,tmp)
                    tmp.replace(path)
                    records[path.name]=dict(card_id=card['id'],role=role,source_text_sha256=digest(card[role]),speech_text=text,speech_sha256=digest(text),fingerprint=fingerprint,bytes=path.stat().st_size,duration_seconds=round(duration,3),audio_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),validation='ffprobe duration and full ffmpeg decode passed')
                    checkpoint()
                    print('OK',path.name,round(duration,1),'sec',flush=True);return
                except Exception as e:
                    tmp.unlink(missing_ok=True)
                    print('RETRY',path.name,attempt+1,type(e).__name__,str(e)[:200],flush=True)
                    await asyncio.sleep(min(2**attempt,16))
            failures.append(path.name)
    cards=deck['cards'][:args.limit] if args.limit else deck['cards']
    await asyncio.gather(*(make(c,r) for c in cards for r in ('question','answer')))
    metadata['expected_files']=len(deck['cards'])*2
    metadata['generated_files']=len(records)
    metadata['failures']=failures
    metadata['complete']=len(records)==len(deck['cards'])*2 and not failures
    checkpoint()
    print('SUMMARY',len(records),'/',metadata['expected_files'],'failures',len(failures),flush=True)
    return 1 if failures else 0

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--limit',type=int,default=0);p.add_argument('--concurrency',type=int,default=3)
    args=p.parse_args()
    if args.concurrency<1: p.error('concurrency must be positive')
    sys.exit(asyncio.run(run(args)))
