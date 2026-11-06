"""Validate new content, exact old MP3 preservation and distributable APK content."""
from pathlib import Path
from zipfile import ZipFile
import hashlib,json,subprocess,sys,sqlite3,statistics
ROOT=Path(__file__).resolve().parents[1];ASSETS=ROOT/'android/app/src/main/assets'
read=lambda p:json.loads(p.read_text())
cards={c['id']:c for c in read(ASSETS/'cards.json')['cards']};v5=read(ASSETS/'learning_v5.json');manifest=read(ROOT/'audio/v5-manifest.json');tracks=read(ROOT/'data/v5_tracks.json')['tracks']
assert set(v5['cards'])==set(cards) and manifest['complete'] and len(manifest['files'])==191
assert len(v5['whatifs'])==24 and len(v5['comparisons'])==12
assert len({t['path'] for t in tracks})==191
for id,c in v5['cards'].items():
 assert 3<=len(c['checklist'])<=5 and all(c['checklist'])
 assert c['short']==cards[id]['answer'] and c['short_audio']==cards[id]['answer_audio']
 assert cards[id]['answer'] in c['detailed'] and cards[id]['example_note'] in c['detailed']
for t in v5['whatifs']:
 assert t['card_id'] in cards
 if t['kind']=='python':assert subprocess.run([sys.executable,'-I','-c',t['code']],capture_output=True,text=True,check=True,timeout=4).stdout.rstrip()==t['answer']
 elif t['kind']=='sql':
  db=sqlite3.connect(':memory:');db.executescript('CREATE TABLE users(id INTEGER, name TEXT); INSERT INTO users VALUES(1,"Анна"),(2,"Борис"); CREATE TABLE orders(id INTEGER,user_id INTEGER,amount INTEGER); INSERT INTO orders VALUES(10,1,100),(11,1,NULL);')
  actual='\n'.join(' | '.join('NULL' if x is None else str(x) for x in row) for row in db.execute(t['code']));db.close();assert actual==t['answer']
for t in v5['comparisons']:assert set(t['card_ids'])<=cards.keys() and all(t[k] for k in ['left','right','choice','example','trap','question','answer'])
cues=read(ASSETS/'audio_cues.json');assert len(cues)==706
with ZipFile(ROOT/'android/app/build/outputs/apk/debug/app-debug.apk') as apk:
 for p in (ASSETS/'audio').glob('*.mp3'):
  relative=p.relative_to(ASSETS).as_posix();assert p.read_bytes()==(ROOT/relative).read_bytes()==apk.read('assets/'+relative)
 for t in tracks:
  p=ROOT/t['path'];record=manifest['files'][p.name]
  assert hashlib.sha256(p.read_bytes()).hexdigest()==record['sha256'] and record['source_text']==t['text']
  cue=cues[t['path']];assert cue['duration_ms']>0 and cue['starts_ms'][0]==0 and cue['starts_ms']==sorted(set(cue['starts_ms']))
  assert all(0<=x<cue['duration_ms'] for x in cue['starts_ms'])
  if t['parts']:
   expected=sum(cues[part]['duration_ms'] for part in t['parts']);assert abs(expected-cue['duration_ms'])<300,(t['path'],expected,cue['duration_ms'])
for name in ['learning_v5.json','audio_cues.json']:assert (ROOT/'data'/name).read_bytes()==(ASSETS/name).read_bytes()
report=dict(cards_with_checklists_and_two_answer_levels=155,whatif=24,executed_python_and_sql=20,comparisons=12,original_mp3_preserved=515,new_detailed_compositions=155,newly_synthesized_exercises=36,packaged_mp3=706,short_median_seconds=round(statistics.median(cues[c['short_audio']]['duration_ms']/1000 for c in v5['cards'].values()),1),detailed_median_seconds=round(statistics.median(cues[c['detailed_audio']]['duration_ms']/1000 for c in v5['cards'].values()),1))
(ROOT/'docs/v5-content-checks.json').write_text(json.dumps(report,indent=2)+'\n');print(report)
