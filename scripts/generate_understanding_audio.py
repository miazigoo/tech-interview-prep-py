import asyncio,hashlib,json,sys
from pathlib import Path
from datetime import datetime,timezone
import edge_tts
from generate_audio import speech,inspect_audio,VOICE,RATE
ROOT=Path(__file__).resolve().parents[1]
async def main():
    tracks=json.loads((ROOT/'data/understanding.json').read_text())['tracks']
    glossary=json.loads((ROOT/'data/pronunciations.json').read_text())
    path=ROOT/'audio/understanding-manifest.json'
    previous=json.loads(path.read_text()) if path.exists() else {}
    files=previous.get('files',{});failures=[]
    manifest=dict(voice=VOICE,rate=RATE,client_version=edge_tts.__version__,created_at=datetime.now(timezone.utc).isoformat(),expected=len(tracks),files=files,complete=False)
    def save():
        tmp=path.with_suffix('.part');tmp.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n');tmp.replace(path)
    sem=asyncio.Semaphore(6)
    async def one(t):
        text=speech(t['text'],glossary)
        fingerprint=hashlib.sha256(json.dumps([text,VOICE,RATE,edge_tts.__version__]).encode()).hexdigest()
        out=ROOT/t['path'];tmp=out.with_suffix('.part.mp3')
        async with sem:
            record=files.get(out.name,{})
            if out.exists() and record.get('fingerprint')==fingerprint:
                try:
                    duration=await asyncio.to_thread(inspect_audio,out)
                    if record.get('sha256')==hashlib.sha256(out.read_bytes()).hexdigest():return
                except Exception:pass
            for attempt in range(8):
                try:
                    await asyncio.wait_for(edge_tts.Communicate(text,VOICE,rate=RATE).save(str(tmp)),120)
                    duration=await asyncio.to_thread(inspect_audio,tmp);tmp.replace(out)
                    files[out.name]=dict(source_text=t['text'],speech_text=text,fingerprint=fingerprint,sha256=hashlib.sha256(out.read_bytes()).hexdigest(),duration_seconds=round(duration,3),bytes=out.stat().st_size)
                    save();print('OK',len(files),'/',len(tracks),out.name,flush=True);return
                except Exception as e:
                    tmp.unlink(missing_ok=True);print('RETRY',t['id'],attempt+1,type(e).__name__,flush=True);await asyncio.sleep(min(2**attempt,16))
            failures.append(out.name)
    save();await asyncio.gather(*(one(t) for t in tracks))
    manifest['failures']=failures;manifest['complete']=not failures and all(Path(t['path']).name in files for t in tracks);save()
    print('COMPLETE',manifest['complete'],len(files),flush=True)
    return 0 if manifest['complete'] else 1
if __name__=='__main__':sys.exit(asyncio.run(main()))
