import asyncio,hashlib,json,sys
from pathlib import Path
from datetime import datetime,timezone
import edge_tts
import subprocess,tempfile
from generate_audio import speech,inspect_audio,VOICE,RATE
ROOT=Path(__file__).resolve().parents[1]
async def main():
    tracks=json.loads((ROOT/'data/v5_tracks.json').read_text())['tracks']
    glossary=json.loads((ROOT/'data/pronunciations.json').read_text())
    path=ROOT/'audio/v5-manifest.json'
    previous=json.loads(path.read_text()) if path.exists() else {}
    expected_names={Path(t['path']).name for t in tracks}
    files={k:v for k,v in previous.get('files',{}).items() if k in expected_names};failures=[]
    manifest=dict(voice=VOICE,rate=RATE,client_version=edge_tts.__version__,created_at=datetime.now(timezone.utc).isoformat(),expected=len(tracks),files=files,complete=False)
    def save():
        tmp=path.with_suffix('.part');tmp.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n');tmp.replace(path)
    sem=asyncio.Semaphore(12)
    async def one(t):
        text=speech(t['text'],glossary)
        parts=t.get('parts')
        fingerprint=hashlib.sha256(json.dumps([text,VOICE,RATE,edge_tts.__version__,[hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in parts] if parts else None]).encode()).hexdigest()
        out=ROOT/t['path'];tmp=out.with_suffix('.part.mp3')
        async with sem:
            record=files.get(out.name,{})
            if out.exists() and record.get('fingerprint')==fingerprint:
                try:
                    duration=await asyncio.to_thread(inspect_audio,out)
                    if record.get('sha256')==hashlib.sha256(out.read_bytes()).hexdigest():return
                except Exception:pass
            for attempt in range(12):
                try:
                    if parts:
                        def compose():
                            with tempfile.NamedTemporaryFile(mode='w',suffix='.txt') as listing:
                                listing.write(''.join("file '"+str(ROOT/p)+"'\n" for p in parts));listing.flush()
                                subprocess.run(['ffmpeg','-v','error','-f','concat','-safe','0','-i',listing.name,'-c:a','copy','-y',str(tmp)],check=True,capture_output=True)
                        await asyncio.to_thread(compose)
                    else:
                        await asyncio.wait_for(edge_tts.Communicate(text,VOICE,rate=RATE,connect_timeout=10,receive_timeout=25).save(str(tmp)),60)
                    duration=await asyncio.to_thread(inspect_audio,tmp);tmp.replace(out)
                    files[out.name]=dict(source_text=t['text'],speech_text=text,fingerprint=fingerprint,sha256=hashlib.sha256(out.read_bytes()).hexdigest(),duration_seconds=round(duration,3),bytes=out.stat().st_size,source_parts=parts)
                    save();print('OK',len(files),'/',len(tracks),out.name,flush=True);return
                except Exception as e:
                    tmp.unlink(missing_ok=True);print('RETRY',t['id'],attempt+1,type(e).__name__,flush=True);await asyncio.sleep(min(2**attempt,6))
            failures.append(out.name)
    save();await asyncio.gather(*(one(t) for t in tracks))
    manifest['failures']=failures;manifest['complete']=not failures and all(Path(t['path']).name in files for t in tracks);save()
    print('COMPLETE',manifest['complete'],len(files),flush=True)
    return 0 if manifest['complete'] else 1
if __name__=='__main__':sys.exit(asyncio.run(main()))
